package com.orbital.market

import com.orbital.market.plugins.configureRouting
import com.orbital.plugins.configureMonitoring
import com.orbital.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
  embeddedServer(Netty, port = 8081, host = "0.0.0.0", module = Application::module)
      .start(wait = true)
}

fun Application.module() {
  configureSerialization()
  configureMonitoring("market")
  configureRouting()
}
