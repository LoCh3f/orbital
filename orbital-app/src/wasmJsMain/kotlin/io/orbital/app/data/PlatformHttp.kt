package io.orbital.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient =
    HttpClient(Js) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }

// Browsers have no environment variables; the gateway is assumed to be reachable
// on localhost during local development.
actual fun defaultGatewayUrl(): String = "http://127.0.0.1:8080"
