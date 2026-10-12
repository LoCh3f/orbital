package com.orbital.gateway

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RateLimitForwardedHeadersTest {
  private fun io.ktor.server.application.Application.remoteHostProbe(trustProxyHeaders: Boolean) {
    configureRateLimit(trustProxyHeaders)
    routing { get("/__remote-host") { call.respondText(call.request.origin.remoteHost) } }
  }

  @Test
  fun `remoteHost ignores X-Forwarded-For by default`() = testApplication {
    application { remoteHostProbe(trustProxyHeaders = false) }
    val response = client.get("/__remote-host") { header("X-Forwarded-For", "203.0.113.5") }
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText() != "203.0.113.5")
  }

  @Test
  fun `remoteHost honors X-Forwarded-For when proxy headers are trusted`() = testApplication {
    application { remoteHostProbe(trustProxyHeaders = true) }
    val response = client.get("/__remote-host") { header("X-Forwarded-For", "203.0.113.5") }
    assertEquals("203.0.113.5", response.bodyAsText())
  }

  @Test
  fun `rejects requests once an injected rate limiter denies them`() = testApplication {
    var calls = 0
    val limiter =
        object : RateLimiter {
          override fun tryAcquire(key: String): Boolean = (calls++) < 1
        }
    application {
      configureRateLimit(trustProxyHeaders = false, rateLimiter = limiter)
      routing { get("/__probe") { call.respondText("ok") } }
    }

    val first = client.get("/__probe")
    assertEquals(HttpStatusCode.OK, first.status)

    val second = client.get("/__probe")
    assertEquals(HttpStatusCode.TooManyRequests, second.status)
  }
}
