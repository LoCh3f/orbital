package com.orbital.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * Installs JSON content negotiation (pretty-printed, lenient parsing) for request/response bodies.
 */
fun Application.configureSerialization() {
  install(ContentNegotiation) {
    json(
        Json {
          prettyPrint = true
          isLenient = true
        })
  }
}
