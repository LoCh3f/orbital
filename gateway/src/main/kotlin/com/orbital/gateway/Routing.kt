package com.orbital.gateway

import com.orbital.models.Asset
import com.orbital.models.NewsArticle
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private const val BITCOIN_PRICE = 45000.0
private const val BITCOIN_CHANGE = 500.0
private const val BITCOIN_MARKET_CAP = 880000000000L
private const val ETHEREUM_PRICE = 3000.0
private const val ETHEREUM_CHANGE = 50.0
private const val ETHEREUM_MARKET_CAP = 360000000000L

fun Application.configureRouting() {
  routing {
    get("/") { call.respond(mapOf("message" to "Orbital Gateway API")) }

    // Mock endpoints per testing
    get("/assets") {
      val assets =
          listOf(
              Asset("bitcoin", "BTC", "Bitcoin", BITCOIN_PRICE, BITCOIN_CHANGE, BITCOIN_MARKET_CAP),
              Asset(
                  "ethereum",
                  "ETH",
                  "Ethereum",
                  ETHEREUM_PRICE,
                  ETHEREUM_CHANGE,
                  ETHEREUM_MARKET_CAP))
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
