@file:Suppress("TooGenericExceptionThrown", "EmptyFunctionBlock")

package com.orbital.news.plugins

import com.orbital.news.api.NewsApiClient
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
}
