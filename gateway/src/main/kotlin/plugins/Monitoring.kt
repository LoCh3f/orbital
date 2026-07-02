package com.orbital.gateway.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureMonitoring() {
  routing { get("/health") { call.respond(mapOf("status" to "OK", "service" to "gateway")) } }
}
