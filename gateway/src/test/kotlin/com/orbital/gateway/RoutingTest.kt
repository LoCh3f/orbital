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

  @Test
  fun `market prices proxy falls back to upstream when cache read throws`() = testApplication {
    val mockClient =
        HttpClient(
            MockEngine { _ ->
              respond(
                  content = """{"data":[]}""",
                  status = HttpStatusCode.OK,
                  headers = headersOf(HttpHeaders.ContentType, "application/json"))
            })
    @Suppress("TooGenericExceptionThrown")
    val throwingCache =
        object : Cache {
          override fun get(key: String): String? = throw RuntimeException("redis down")

          override fun setex(key: String, ttlSeconds: Int, value: String) {
            // No-op for testing
          }
        }
    application { configureRouting(client = mockClient, cache = throwingCache) }

    val response = client.get("/api/v1/market/prices")

    assertEquals(HttpStatusCode.OK, response.status)
    assertEquals("""{"data":[]}""", response.bodyAsText())
  }
}

class MemoryCacheTest {
  @Test
  fun `setex evicts the soonest-to-expire entry once the cache is full`() {
    val cache = MemoryCache(maxEntries = 2)
    cache.setex("a", ttlSeconds = 100, value = "A")
    cache.setex("b", ttlSeconds = 1, value = "B")
    cache.setex("c", ttlSeconds = 100, value = "C")

    assertEquals("A", cache.get("a"))
    assertEquals(null, cache.get("b"))
    assertEquals("C", cache.get("c"))
  }

  @Test
  fun `setex never grows the cache past maxEntries`() {
    val cache = MemoryCache(maxEntries = 5)
    repeat(50) { i -> cache.setex("key-$i", ttlSeconds = 100, value = "v$i") }
    assertTrue(cache.size() <= 5)
  }
}
