package com.orbital.plugins

import com.orbital.core.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Installs JSON content negotiation (pretty-printed, lenient parsing) for request/response bodies.
 */
fun Application.configureSerialization() {
  install(StatusPages) {
    exception<Throwable> { call, cause ->
      if (cause is CancellationException) throw cause
      call.application.environment.log.error("Request failed", cause)
      val status =
          if (cause is IllegalArgumentException) HttpStatusCode.BadRequest
          else HttpStatusCode.BadGateway
      call.respond(
          status,
          ApiResponse.Error(
              status.value,
              if (status == HttpStatusCode.BadRequest) "Invalid request"
              else "Service temporarily unavailable"))
    }
  }
  install(ContentNegotiation) {
    json(
        Json {
          prettyPrint = true
          isLenient = true
        })
  }
}
