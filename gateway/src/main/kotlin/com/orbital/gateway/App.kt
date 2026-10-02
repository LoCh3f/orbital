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
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
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
  configureRateLimit(trustProxyHeaders = trustProxyHeadersFromEnv())
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

/**
 * Global limit of [REQUESTS_PER_MINUTE] requests per minute, keyed by remote host. When
 * [trustProxyHeaders] is true, installs [XForwardedHeaders] first so `remoteHost` reflects the
 * original client's address instead of a reverse proxy's — only enable this (via
 * `TRUST_PROXY_HEADERS=true`) when the gateway sits behind infrastructure you control that
 * overwrites any client-supplied `X-Forwarded-For` header; otherwise a direct client could spoof it
 * to bypass rate limiting entirely.
 */
internal fun Application.configureRateLimit(trustProxyHeaders: Boolean) {
  if (trustProxyHeaders) install(XForwardedHeaders)
  install(RateLimit) {
    global {
      rateLimiter(limit = REQUESTS_PER_MINUTE, refillPeriod = 1.minutes)
      requestKey { call -> call.request.origin.remoteHost }
    }
  }
}

private fun trustProxyHeadersFromEnv(): Boolean =
    System.getenv("TRUST_PROXY_HEADERS")?.equals("true", ignoreCase = true) == true
