package com.orbital.gateway.plugins

import com.orbital.models.Asset
import com.orbital.models.NewsArticle
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
  routing {
    get("/") { call.respond(mapOf("message" to "Orbital Gateway API")) }

    // Mock endpoints per testing
    get("/assets") {
      val assets =
          listOf(
              Asset("bitcoin", "BTC", "Bitcoin", 45000.0, 500.0, 880000000000),
              Asset("ethereum", "ETH", "Ethereum", 3000.0, 50.0, 360000000000))
      call.respond(assets)
    }

    get("/news") {
      val news =
          listOf(
              NewsArticle(
                  "1",
                  "Bitcoin Reaches New High",
                  "BTC surges amid market optimism",
                  "CryptoNews",
                  "2024-01-15T10:30:00Z",
                  "https://example.com/news/1"))
      call.respond(news)
    }
  }
}
