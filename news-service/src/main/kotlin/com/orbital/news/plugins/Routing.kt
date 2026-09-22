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

fun Application.configureRouting(
    appScope: CoroutineScope,
    newsApiClient: NewsApiClient = NewsApiClient()
) {
  // Redis cache (optional)
  val redisUrl = System.getenv("REDIS_URL")
  val jedisPool = redisUrl?.let { JedisPool(URI(it)) }

  val logger = LoggerFactory.getLogger("NewsRouting")

  routing {
    get("/health") { call.respond(mapOf("status" to "healthy", "service" to "news")) }

    get("/api/v1/news") {
      val category = call.request.queryParameters["category"]
      val cacheKey = "news:category:${category ?: "all"}"
      val requestedCategory =
          category?.let { raw ->
            NewsCategory.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
          } ?: NewsCategory.CRYPTO

      // Try Redis cache first
      val cached = jedisPool?.resource?.use { jedis -> jedis.get(cacheKey) }
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

      // Persist fetched news asynchronously (best-effort) using application scope
      appScope.launch {
        try {
          com.orbital.news.persistence.NewsRepository.saveAll(payload)
        } catch (e: Exception) {
          logger.warn("Failed to persist news", e)
        }
      }

      // Cache payload in Redis (short TTL)
      try {
        jedisPool?.resource?.use { jedis ->
          val json = Json.encodeToString(ListSerializer(NewsArticle.serializer()), payload)
          jedis.setex(cacheKey, 60, json)
        }
      } catch (e: Exception) {
        // best-effort cache
      }

      call.respond(ApiResponse.Success(payload))
    }
  }
}
