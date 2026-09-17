package com.orbital.gateway

import com.orbital.plugins.configureMonitoring
import com.orbital.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS

fun main() {
  embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
      .start(wait = true)
}

fun Application.module() {
  configureSerialization()
  configureMonitoring("gateway")
  configureCors()
  configureRouting()
}

private fun Application.configureCors() {
  install(CORS) {
    anyHost()
    allowHeader(io.ktor.http.HttpHeaders.ContentType)
  }
}
