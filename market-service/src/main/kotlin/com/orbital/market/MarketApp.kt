@file:Suppress("TooGenericExceptionCaught", "PrintStackTrace")

package com.orbital.market

import com.orbital.market.plugins.configureRouting
import com.orbital.plugins.configureMonitoring
import com.orbital.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory

fun main() {
  embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
      .start(wait = true)
}

fun Application.module() {
  // Application-scoped coroutine scope for background tasks (structured concurrency)
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  environment.monitor.subscribe(ApplicationStopped) { appScope.cancel() }

  configureSerialization()
  configureMonitoring("market")

  // Initialize persistence if env provided
  val jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://127.0.0.1:5432/orbital"
  val dbUser = System.getenv("DB_USER") ?: "orbital"
  val dbPass = System.getenv("DB_PASSWORD") ?: "orbital"
  val logger = LoggerFactory.getLogger("MarketApp")
  try {
    com.orbital.market.persistence.MarketRepository.initDatabase(jdbcUrl, dbUser, dbPass)
  } catch (e: Exception) {
    // Log and continue; DB is optional for dev
    logger.warn("DB init failed, continuing without DB", e)
  }

  configureRouting(appScope)
}
