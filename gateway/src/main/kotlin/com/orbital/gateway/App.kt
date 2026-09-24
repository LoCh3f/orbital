package com.orbital.gateway

import com.orbital.plugins.configureMetrics
import com.orbital.plugins.configureMonitoring
import com.orbital.plugins.configureRequestTracing
import com.orbital.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import kotlin.time.Duration.Companion.minutes

private const val REQUESTS_PER_MINUTE = 60

/** Starts the gateway's Netty server on port 8080. */
fun main() {
  embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
      .start(wait = true)
}

/**
 * Wires up serialization, health/metrics, request tracing, rate limiting, CORS, and the proxy
 * routes.
 */
fun Application.module() {
  configureSerialization()
  configureMonitoring("gateway")
  configureMetrics("gateway")
  configureRequestTracing()
  configureRateLimit()
  configureCors()
  configureRouting()
}

/**
 * Permissive by default so any origin (including the web `orbital-app` build) can call this API
 * during local development. Set `CORS_ALLOWED_HOST` to restrict to a single HTTPS origin in
 * production deployments.
 */
private fun Application.configureCors() {
  install(CORS) {
    // Permissive by default (local dev, docker-compose — nothing sets this var there).
    // Set CORS_ALLOWED_HOST (e.g. "loch3f.github.io") to restrict to a specific origin.
    val allowedHost = System.getenv("CORS_ALLOWED_HOST")
    if (allowedHost.isNullOrBlank()) {
      anyHost()
    } else {
      allowHost(allowedHost, schemes = listOf("https"))
    }
    allowHeader(io.ktor.http.HttpHeaders.ContentType)
  }
}

/** Global limit of [REQUESTS_PER_MINUTE] requests per minute, keyed by remote host. */
private fun Application.configureRateLimit() {
  install(RateLimit) {
    global {
      rateLimiter(limit = REQUESTS_PER_MINUTE, refillPeriod = 1.minutes)
      requestKey { call -> call.request.origin.remoteHost }
    }
  }
}
