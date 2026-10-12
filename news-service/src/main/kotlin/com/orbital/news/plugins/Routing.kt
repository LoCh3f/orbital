@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "PrintStackTrace", "MagicNumber")

package com.orbital.news.plugins

import com.orbital.core.ApiResponse
import com.orbital.models.NewsArticle
import com.orbital.models.NewsCategory
import com.orbital.news.api.NewsApiClient
import com.orbital.news.api.NewsMapper
import com.orbital.news.persistence.NewsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisSentinelPool
import redis.clients.jedis.util.Pool

/** Minimal read/write cache abstraction so the Redis-backed lookup is swappable in tests. */
internal interface NewsCache {
  fun get(key: String): String?

  fun setex(key: String, ttlSeconds: Int, value: String)
}

/**
 * [NewsCache] backed by Redis. [pool] is typed to the common base so either a plain [JedisPool] or
 * a [JedisSentinelPool] (see [defaultNewsCache]) works here unchanged.
 */
internal class RedisNewsCache(private val pool: Pool<Jedis>) : NewsCache {
  override fun get(key: String): String? = pool.resource.use { it.get(key) }

  override fun setex(key: String, ttlSeconds: Int, value: String) {
    pool.resource.use { it.setex(key, ttlSeconds.toLong(), value) }
  }
}

/**
 * Prefers a Sentinel-monitored Redis (`REDIS_SENTINEL_HOSTS`, comma-separated `host:port` pairs,
 * plus `REDIS_MASTER_NAME`) over a single-instance one (`REDIS_URL`) — mirrors
 * `com.orbital.gateway.defaultCache`'s reasoning for why Sentinel mode matters once more than one
 * news-service instance is running.
 */
private fun defaultNewsCache(): NewsCache? {
  val sentinelHosts = System.getenv("REDIS_SENTINEL_HOSTS")
  if (!sentinelHosts.isNullOrBlank()) {
    val masterName = System.getenv("REDIS_MASTER_NAME") ?: "mymaster"
    val sentinels = sentinelHosts.split(",").map { it.trim() }.toSet()
    return RedisNewsCache(JedisSentinelPool(masterName, sentinels))
  }
  return System.getenv("REDIS_URL")?.let { RedisNewsCache(JedisPool(URI(it))) }
}

/**
 * Registers `/health`, `GET /api/v1/news`, and `GET /api/v1/news/history`. The news route resolves
 * `?category=` case-insensitively (defaulting to [NewsCategory.CRYPTO]), serves from a short-lived
 * Redis cache when available, and otherwise fetches via [newsApiClient] (currently always the
 * mocked fallback — see [NewsApiClient]), persisting the result to Postgres asynchronously and
 * best-effort. The history route reads that persisted archive back — our own stored articles, not a
 * live upstream fetch — so it still answers (with whatever's been seen so far) even if the news
 * source is down.
 */
internal fun Application.configureRouting(
    appScope: CoroutineScope,
    newsApiClient: NewsApiClient = NewsApiClient(),
    cache: NewsCache? = defaultNewsCache()
) {
  val logger = LoggerFactory.getLogger("NewsRouting")

  routing {
    get("/api/v1/news") {
      val category = call.request.queryParameters["category"]
      val cacheKey = "news:category:${category ?: "all"}"
      val requestedCategory =
          category?.let { raw ->
            NewsCategory.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
          } ?: NewsCategory.CRYPTO

      val cached =
          try {
            cache?.get(cacheKey)
          } catch (e: Exception) {
            logger.warn("Cache read failed for key '$cacheKey', falling back to upstream fetch", e)
            null
          }
      if (!cached.isNullOrBlank()) {
        try {
          val cachedArticles =
              Json.decodeFromString(ListSerializer(NewsArticle.serializer()), cached)
          call.respond(ApiResponse.Success(cachedArticles))
          return@get
        } catch (e: Exception) {
          // decode failed; fall through to fetch
        }
      }

      val result = runCatching { newsApiClient.fetchTopHeadlines(category) }
      if (result.isFailure) {
        val error = result.exceptionOrNull()
        call.respond(
            HttpStatusCode.BadGateway,
            ApiResponse.Error(
                HttpStatusCode.BadGateway.value, error?.message ?: "Failed to fetch news"))
        return@get
      }

      val payload = result.getOrThrow().map { NewsMapper.toDomain(it, requestedCategory) }

      appScope.launch {
        try {
          NewsRepository.saveAll(payload)
        } catch (e: Exception) {
          logger.warn("Failed to persist news", e)
        }
      }

      try {
        val json = Json.encodeToString(ListSerializer(NewsArticle.serializer()), payload)
        cache?.setex(cacheKey, 60, json)
      } catch (e: Exception) {
        // best-effort cache
      }

      call.respond(ApiResponse.Success(payload))
    }

    historyRoute()
  }
}

/**
 * `GET /api/v1/news/history` — stored articles, most recent first, from [NewsRepository] (our own
 * persisted archive, not a live upstream fetch), optionally filtered by `?category=`.
 */
private fun Route.historyRoute() {
  get("/api/v1/news/history") {
    val category = call.request.queryParameters["category"]
    val requestedCategory =
        category?.let { raw ->
          NewsCategory.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
    val result = runCatching { NewsRepository.getHistory(requestedCategory) }
    if (result.isFailure) {
      call.respond(
          HttpStatusCode.InternalServerError,
          ApiResponse.Error(
              HttpStatusCode.InternalServerError.value,
              result.exceptionOrNull()?.message ?: "Failed to fetch news history"))
      return@get
    }
    call.respond(ApiResponse.Success(result.getOrThrow()))
  }
}
