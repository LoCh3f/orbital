package com.orbital.news.plugins

import com.orbital.news.api.NewsApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

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

private fun io.ktor.server.application.Application.configureTestApp() {
  install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
  configureRouting(TEST_SCOPE, newsApiClient())
}

class RoutingTest {
  @Test
  fun `health route returns healthy status`() = testApplication {
    application { configureTestApp() }
    val response = client.get("/health")
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("healthy"))
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
}
