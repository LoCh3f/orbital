@file:Suppress("TooGenericExceptionThrown", "EmptyFunctionBlock")

package com.orbital.news.plugins

import com.orbital.models.NewsArticle
import com.orbital.models.NewsCategory
import com.orbital.news.api.NewsApiClient
import com.orbital.news.persistence.NewsRepository
import com.orbital.plugins.configureMonitoring
import com.orbital.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

private val TEST_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val VALID_NEWS_API_JSON =
    """{"status":"ok","totalResults":1,"articles":[{"title":"Orbital launch",
    "description":"A new release is here","url":"https://example.com/news",
    "source":{"name":"Orbital"},"publishedAt":"2024-01-01T00:00:00Z"}]}"""

private fun newsApiClient() =
    NewsApiClient(
        HttpClient(
            MockEngine { _ ->
              respond(
                  content = VALID_NEWS_API_JSON,
                  status = HttpStatusCode.OK,
                  headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }))

private fun io.ktor.server.application.Application.configureTestApp(cache: NewsCache? = null) {
  configureSerialization()
  configureMonitoring("news")
  configureRouting(TEST_SCOPE, newsApiClient(), cache = cache)
}

class RoutingTest {
  @Test
  fun `health route returns OK status`() = testApplication {
    application { configureTestApp() }
    val response = client.get("/health")
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("OK"))
  }

  @Test
  fun `news route honors the requested category`() = testApplication {
    application { configureTestApp() }
    val response = client.get("/api/v1/news?category=markets")
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("\"MARKETS\""))
  }

  @Test
  fun `news route defaults to crypto category when omitted`() = testApplication {
    application { configureTestApp() }
    val response = client.get("/api/v1/news")
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("\"CRYPTO\""))
  }

  @Test
  fun `news route falls back to upstream when cache read throws`() = testApplication {
    val throwingCache =
        object : NewsCache {
          override fun get(key: String): String? = throw RuntimeException("redis down")

          override fun setex(key: String, ttlSeconds: Int, value: String) {}
        }
    application { configureTestApp(cache = throwingCache) }

    val response = client.get("/api/v1/news")

    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("\"CRYPTO\""))
  }

  @Test
  fun `history route returns stored articles, filtered by category`() = runBlocking {
    NewsRepository.initDatabase(
        "jdbc:h2:mem:news-routing-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
    NewsRepository.saveAll(
        listOf(
            NewsArticle(
                id = UUID.randomUUID(),
                title = "A markets headline",
                description = "desc",
                url = "https://example.com/markets",
                sourceName = "Example",
                publishedAt = Instant.parse("2024-01-01T00:00:00Z"),
                category = NewsCategory.MARKETS),
            NewsArticle(
                id = UUID.randomUUID(),
                title = "A tech headline",
                description = "desc",
                url = "https://example.com/tech",
                sourceName = "Example",
                publishedAt = Instant.parse("2024-01-01T00:00:00Z"),
                category = NewsCategory.TECH)))

    testApplication {
      application { configureTestApp() }

      val filtered = client.get("/api/v1/news/history?category=markets")
      assertEquals(HttpStatusCode.OK, filtered.status)
      assertTrue(filtered.bodyAsText().contains("A markets headline"))
      assertTrue(!filtered.bodyAsText().contains("A tech headline"))

      val all = client.get("/api/v1/news/history")
      assertEquals(HttpStatusCode.OK, all.status)
      assertTrue(all.bodyAsText().contains("A markets headline"))
      assertTrue(all.bodyAsText().contains("A tech headline"))
    }
  }
}
