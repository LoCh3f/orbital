package com.orbital.gateway

import com.orbital.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingTest {
  @Test
  fun `root route returns welcome message`() = testApplication {
    application {
      configureSerialization()
      configureRouting()
    }
    val response = client.get("/")
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("Orbital Gateway API"))
  }

  @Test
  fun `market prices proxy forwards upstream body with json content type`() = testApplication {
    val mockClient =
        HttpClient(
            MockEngine { _ ->
              respond(
                  content = """{"data":[]}""",
                  status = HttpStatusCode.OK,
                  headers = headersOf(HttpHeaders.ContentType, "application/json"))
            })
    application { configureRouting(client = mockClient) }

    val response = client.get("/api/v1/market/prices")

    assertEquals(HttpStatusCode.OK, response.status)
    assertEquals("""{"data":[]}""", response.bodyAsText())
    assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
  }

  @Test
  fun `market prices proxy caches successful responses`() = testApplication {
    val callCount = AtomicInteger(0)
    val mockClient =
        HttpClient(
            MockEngine { _ ->
              callCount.incrementAndGet()
              respond(
                  content = """{"data":[]}""",
                  status = HttpStatusCode.OK,
                  headers = headersOf(HttpHeaders.ContentType, "application/json"))
            })
    application { configureRouting(client = mockClient) }

    client.get("/api/v1/market/prices")
    client.get("/api/v1/market/prices")

    assertEquals(1, callCount.get())
  }

  @Test
  fun `news proxy forwards upstream error status`() = testApplication {
    val mockClient =
        HttpClient(
            MockEngine { _ ->
              respond(
                  content = """{"code":502,"message":"upstream down"}""",
                  status = HttpStatusCode.BadGateway,
                  headers = headersOf(HttpHeaders.ContentType, "application/json"))
            })
    application { configureRouting(client = mockClient) }

    val response = client.get("/api/v1/news")

    assertEquals(HttpStatusCode.BadGateway, response.status)
  }
}
