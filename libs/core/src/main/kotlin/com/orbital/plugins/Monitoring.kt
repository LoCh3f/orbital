package com.orbital.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Shared health endpoint. Service name can be provided by each module.
 */
fun Application.configureMonitoring(serviceName: String = "orbital") {
  routing { get("/health") { call.respond(mapOf("status" to "OK", "service" to serviceName)) } }
}
