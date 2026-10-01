@file:Suppress("TooGenericExceptionCaught", "PrintStackTrace")

package com.orbital.news

import com.orbital.news.plugins.configureRouting
import com.orbital.plugins.configureMetrics
import com.orbital.plugins.configureRequestTracing
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** Starts news-service's Netty server on port 8082. */
fun main() {
  embeddedServer(Netty, port = 8082, host = "0.0.0.0", module = Application::module)
      .start(wait = true)
}

/**
 * Wires up serialization, metrics, request tracing, routing, and (best-effort) Postgres persistence
 * — a failed DB connection is logged and swallowed, since the DB is optional in dev.
 */
fun Application.module() {
  // Application-scoped coroutine scope for background tasks (structured concurrency)
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  environment.monitor.subscribe(ApplicationStopped) { appScope.cancel() }

  install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
  configureMetrics("news")
  configureRequestTracing()

  // Initialize persistence if env provided
  val jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://127.0.0.1:5432/orbital"
  val dbUser = System.getenv("DB_USER") ?: "orbital"
  val dbPass = System.getenv("DB_PASSWORD") ?: "orbital"
  val logger = LoggerFactory.getLogger("NewsApp")
  try {
    com.orbital.news.persistence.NewsRepository.initDatabase(jdbcUrl, dbUser, dbPass)
  } catch (e: Exception) {
    // Log and continue; DB is optional for dev
    logger.warn("DB init failed, continuing without DB", e)
  }

  configureRouting(appScope)
}
