package io.orbital.app.data

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient

expect fun defaultGatewayUrl(): String
