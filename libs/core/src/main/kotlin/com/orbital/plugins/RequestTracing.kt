package com.orbital.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import java.util.UUID

/**
 * Reads `X-Request-Id` from the incoming request (set by the gateway, forwarded downstream) or
 * generates one if absent, then makes it available to every log line for the request's lifetime via
 * SLF4J's MDC (`callId`) — see each service's `logback.xml`, which includes it in the JSON output.
 * This is what lets one logical request be correlated across gateway/market/news logs.
 */
fun Application.configureRequestTracing() {
  install(CallId) {
    header(HttpHeaders.XRequestId)
    generate { UUID.randomUUID().toString() }
    verify { it.isNotBlank() }
  }
  install(CallLogging) { callIdMdc("callId") }
}
