@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "PrintStackTrace", "MagicNumber")

package com.orbital.news.plugins

import com.orbital.core.ApiResponse
import com.orbital.models.NewsArticle
import com.orbital.models.NewsCategory
import com.orbital.news.api.NewsApiClient
import com.orbital.news.api.NewsMapper
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool

/** Minimal read/write cache abstraction so the Redis-backed lookup is swappable in tests. */
internal interface NewsCache {
  fun get(key: String): String?

  fun setex(key: String, ttlSeconds: Int, value: String)
}

internal class RedisNewsCache(private val pool: JedisPool) : NewsCache {
  override fun get(key: String): String? = pool.resource.use { it.get(key) }

  override fun setex(key: String, ttlSeconds: Int, value: String) {
    pool.resource.use { it.setex(key, ttlSeconds.toLong(), value) }
  }
}

private fun defaultNewsCache(): NewsCache? =
    System.getenv("REDIS_URL")?.let { RedisNewsCache(JedisPool(URI(it))) }

/**
 * Registers `/health` and `GET /api/v1/news`. The news route resolves `?category=`
 * case-insensitively (defaulting to [NewsCategory.CRYPTO]), serves from a short-lived Redis cache
 * when available, and otherwise fetches via [newsApiClient] (currently always the mocked fallback —
 * see [NewsApiClient]), persisting the result to Postgres asynchronously and best-effort.
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
          com.orbital.news.persistence.NewsRepository.saveAll(payload)
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
  }
}
