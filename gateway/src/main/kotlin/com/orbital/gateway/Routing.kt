@file:Suppress("MagicNumber")

package com.orbital.gateway

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import redis.clients.jedis.JedisPool

private val MARKET_SERVICE_URL = System.getenv("MARKET_SERVICE_URL") ?: "http://127.0.0.1:8081"
private val NEWS_SERVICE_URL = System.getenv("NEWS_SERVICE_URL") ?: "http://127.0.0.1:8082"
private const val DEFAULT_CACHE_TTL = 60 // seconds

interface Cache {
  fun get(key: String): String?

  fun setex(key: String, ttlSeconds: Int, value: String)
}

class RedisCache(private val pool: JedisPool) : Cache {
  override fun get(key: String): String? = pool.resource.use { it.get(key) }

  override fun setex(key: String, ttlSeconds: Int, value: String) {
    pool.resource.use { it.setex(key, ttlSeconds.toLong(), value) }
  }
}

private data class MemEntry(val value: String, val expiresAt: Long)

class MemoryCache : Cache {
  private val map = ConcurrentHashMap<String, MemEntry>()

  override fun get(key: String): String? {
    val e = map[key] ?: return null
    return if (System.currentTimeMillis() <= e.expiresAt) e.value
    else {
      map.remove(key)
      null
    }
  }

  override fun setex(key: String, ttlSeconds: Int, value: String) {
    val expires = System.currentTimeMillis() + ttlSeconds * 1000L
    map[key] = MemEntry(value, expires)
  }
}

suspend fun proxyWithCache(
    cache: Cache?,
    cacheKey: String,
    ttlSeconds: Int = DEFAULT_CACHE_TTL,
    block: suspend () -> Pair<HttpStatusCode, String>
): Pair<HttpStatusCode, String> {
  // Try cached entry
  cache?.get(cacheKey)?.let { raw ->
    // stored format: statusCode '\n' body
    val idx = raw.indexOf('\n')
    if (idx > 0) {
      val statusCode = raw.substring(0, idx).toIntOrNull() ?: 200
      val body = raw.substring(idx + 1)
      return HttpStatusCode.fromValue(statusCode) to body
    }
  }

  val (status, body) = block()
  // Cache successful responses
  if (status.value in 200..299) {
    try {
      val toStore = "${status.value}\n$body"
      cache?.setex(cacheKey, ttlSeconds, toStore)
    } catch (_: Exception) {
      // best-effort
    }
  }
  return status to body
}

fun Application.configureRouting(client: HttpClient = HttpClient(CIO)) {
  // Init cache: prefer Redis if REDIS_URL provided
  val redisUrl = System.getenv("REDIS_URL")
  val jedisPool = redisUrl?.let { JedisPool(URI(it)) }
  val cache: Cache? = jedisPool?.let { RedisCache(it) } ?: MemoryCache()

  routing {
    get("/") { call.respond(mapOf("message" to "Orbital Gateway API")) }

    // Proxy market prices to market-service with cache
    get("/api/v1/market/prices") {
      val coinIds = call.request.queryParameters["coinIds"]
      val cacheKey = "market:prices:${coinIds ?: "all"}"

      val (status, body) =
          proxyWithCache(cache, cacheKey) {
            val response: HttpResponse =
                client.get("$MARKET_SERVICE_URL/api/v1/market/prices") {
                  header(HttpHeaders.XRequestId, call.callId)
                  if (!coinIds.isNullOrBlank()) parameter("coinIds", coinIds)
                }
            response.status to response.bodyAsText()
          }

      call.respondText(body, contentType = ContentType.Application.Json, status = status)
    }

    // Proxy news to news-service with cache
    get("/api/v1/news") {
      val category = call.request.queryParameters["category"]
      val cacheKey = "news:category:${category ?: "all"}"

      val (status, body) =
          proxyWithCache(cache, cacheKey) {
            val response: HttpResponse =
                client.get("$NEWS_SERVICE_URL/api/v1/news") {
                  header(HttpHeaders.XRequestId, call.callId)
                  if (!category.isNullOrBlank()) parameter("category", category)
                }
            response.status to response.bodyAsText()
          }

      call.respondText(body, contentType = ContentType.Application.Json, status = status)
    }
  }
}
