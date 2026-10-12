@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

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
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisSentinelPool
import redis.clients.jedis.util.Pool

private val MARKET_SERVICE_URL = System.getenv("MARKET_SERVICE_URL") ?: "http://127.0.0.1:8081"
private val NEWS_SERVICE_URL = System.getenv("NEWS_SERVICE_URL") ?: "http://127.0.0.1:8082"
private const val DEFAULT_CACHE_TTL = 60 // seconds
private val logger = LoggerFactory.getLogger("GatewayRouting")

/**
 * Parses `REDIS_SENTINEL_HOSTS` (comma-separated `host:port` pairs) and `REDIS_MASTER_NAME` into
 * (masterName, sentinelHostPorts), or null if Sentinel isn't configured. Shared by [defaultCache]
 * and `App.kt`'s `defaultRateLimiter` so both back onto the same Sentinel-vs-single-instance
 * decision instead of parsing the env twice.
 */
internal fun sentinelConfigFromEnv(): Pair<String, Set<String>>? {
  val sentinelHosts = System.getenv("REDIS_SENTINEL_HOSTS")
  if (sentinelHosts.isNullOrBlank()) return null
  val masterName = System.getenv("REDIS_MASTER_NAME") ?: "mymaster"
  return masterName to sentinelHosts.split(",").map { it.trim() }.toSet()
}

/**
 * Minimal string cache abstraction the gateway proxies through, backed by [RedisCache] or
 * [MemoryCache].
 */
interface Cache {
  fun get(key: String): String?

  fun setex(key: String, ttlSeconds: Int, value: String)
}

/**
 * [Cache] backed by Redis, used when `REDIS_URL` (a single instance) or `REDIS_SENTINEL_HOSTS` (a
 * Sentinel-monitored primary/replica set — see [defaultCache]) is configured. [pool] is typed to
 * the common base so either a plain [JedisPool] or a [JedisSentinelPool] (which re-discovers the
 * current master on failover) works here unchanged.
 */
class RedisCache(private val pool: Pool<Jedis>) : Cache {
  override fun get(key: String): String? = pool.resource.use { it.get(key) }

  override fun setex(key: String, ttlSeconds: Int, value: String) {
    pool.resource.use { it.setex(key, ttlSeconds.toLong(), value) }
  }
}

private data class MemEntry(val value: String, val expiresAt: Long)

private const val DEFAULT_MAX_ENTRIES = 1000

/**
 * In-process, TTL-expiring [Cache] used as a fallback when no `REDIS_URL` is configured. Bounded at
 * [maxEntries] — once full, expired entries are swept first, then the single soonest-to-expire
 * entry is evicted to make room, so an attacker (or just many distinct query params) can't grow
 * this map without bound.
 */
class MemoryCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) : Cache {
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
    val now = System.currentTimeMillis()
    if (map.size >= maxEntries && !map.containsKey(key)) {
      map.entries.removeAll { it.value.expiresAt <= now }
      if (map.size >= maxEntries) {
        map.keys.minByOrNull { k -> map[k]?.expiresAt ?: 0L }?.let { map.remove(it) }
      }
    }
    map[key] = MemEntry(value, now + ttlSeconds * 1000L)
  }

  internal fun size(): Int = map.size
}

/**
 * Runs [block] (an upstream fetch) behind [cache], keyed by [cacheKey]. A cache hit is stored as
 * `"<statusCode>\n<body>"` and decoded back into the same pair; only 2xx responses are cached, for
 * [ttlSeconds]. Cache writes are best-effort — a failure to write never fails the request.
 */
suspend fun proxyWithCache(
    cache: Cache?,
    cacheKey: String,
    ttlSeconds: Int = DEFAULT_CACHE_TTL,
    block: suspend () -> Pair<HttpStatusCode, String>
): Pair<HttpStatusCode, String> {
  val cached =
      try {
        cache?.get(cacheKey)
      } catch (e: Exception) {
        logger.warn("Cache read failed for key '$cacheKey', falling back to upstream fetch", e)
        null
      }
  cached?.let { raw ->
    val idx = raw.indexOf('\n')
    if (idx > 0) {
      val statusCode = raw.substring(0, idx).toIntOrNull() ?: 200
      val body = raw.substring(idx + 1)
      return HttpStatusCode.fromValue(statusCode) to body
    }
  }

  val (status, body) = block()
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

/**
 * Registers the gateway's proxy routes: `/api/v1/market/prices`, `/api/v1/market/history/{coinId}`
 * → market-service and `/api/v1/news`, `/api/v1/news/history` → news-service, all cached via
 * [proxyWithCache] and forwarded with the request's `X-Request-Id` for cross-service log
 * correlation. Prefers Redis for the cache if `REDIS_URL` is set, otherwise falls back to an
 * in-process [MemoryCache].
 */
fun Application.configureRouting(
    client: HttpClient = HttpClient(CIO),
    cache: Cache? = defaultCache()
) {
  routing {
    get("/") { call.respond(mapOf("message" to "Orbital Gateway API")) }

    marketPricesRoute(client, cache)
    marketHistoryRoute(client, cache)
    newsRoute(client, cache)
    newsHistoryRoute(client, cache)
  }
}

/** Proxies `/api/v1/market/prices` to market-service, cached by the requested `coinIds`. */
private fun Route.marketPricesRoute(client: HttpClient, cache: Cache?) {
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
}

/** Proxies `/api/v1/market/history/{coinId}` to market-service, cached per coin. */
private fun Route.marketHistoryRoute(client: HttpClient, cache: Cache?) {
  get("/api/v1/market/history/{coinId}") {
    val coinId = call.parameters["coinId"]
    if (coinId.isNullOrBlank()) {
      call.respond(HttpStatusCode.BadRequest)
      return@get
    }
    val cacheKey = "market:history:$coinId"

    val (status, body) =
        proxyWithCache(cache, cacheKey) {
          val response: HttpResponse =
              client.get("$MARKET_SERVICE_URL/api/v1/market/history/$coinId") {
                header(HttpHeaders.XRequestId, call.callId)
              }
          response.status to response.bodyAsText()
        }

    call.respondText(body, contentType = ContentType.Application.Json, status = status)
  }
}

/** Proxies `/api/v1/news` to news-service, cached by the requested category. */
private fun Route.newsRoute(client: HttpClient, cache: Cache?) {
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

/** Proxies `/api/v1/news/history` to news-service, cached by the requested category. */
private fun Route.newsHistoryRoute(client: HttpClient, cache: Cache?) {
  get("/api/v1/news/history") {
    val category = call.request.queryParameters["category"]
    val cacheKey = "news:history:${category ?: "all"}"

    val (status, body) =
        proxyWithCache(cache, cacheKey) {
          val response: HttpResponse =
              client.get("$NEWS_SERVICE_URL/api/v1/news/history") {
                header(HttpHeaders.XRequestId, call.callId)
                if (!category.isNullOrBlank()) parameter("category", category)
              }
          response.status to response.bodyAsText()
        }

    call.respondText(body, contentType = ContentType.Application.Json, status = status)
  }
}

/**
 * Prefers a Sentinel-monitored Redis (`REDIS_SENTINEL_HOSTS`, comma-separated `host:port` pairs,
 * plus `REDIS_MASTER_NAME`) over a single-instance one (`REDIS_URL`), falling back to [MemoryCache]
 * when neither is set. Sentinel mode is what actually makes [MemoryCache]'s per-replica-independent
 * problem (see this file's top-level doc comment) go away when running more than one gateway
 * instance: every replica's [JedisSentinelPool] asks the same Sentinel quorum who the current
 * master is, so they all land on one shared cache even across a failover — a single `REDIS_URL`
 * alone can't do that once the host it names stops being the master.
 */
private fun defaultCache(): Cache? {
  sentinelConfigFromEnv()?.let { (masterName, sentinels) ->
    return RedisCache(JedisSentinelPool(masterName, sentinels))
  }
  val redisUrl = System.getenv("REDIS_URL")
  val jedisPool = redisUrl?.let { JedisPool(URI(it)) }
  return jedisPool?.let { RedisCache(it) } ?: MemoryCache()
}
