package com.orbital.market.plugins

import com.orbital.market.api.CoinGeckoClient
import com.orbital.models.Asset
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

private const val TOP_LIMIT = 50
private const val ZERO_MARKET_CAP = 0L

fun Application.configureRouting() {
  val httpClient =
      HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
  val coinGeckoClient = CoinGeckoClient(httpClient)

  routing {
    get("/health") { call.respond(mapOf("status" to "healthy", "service" to "market")) }

    get("/assets") {
      kotlin
          .runCatching { coinGeckoClient.getTopCryptos(TOP_LIMIT) }
          .fold(
              onSuccess = { assets ->
                val domainAssets =
                    assets.map {
                      Asset(
                          id = it.id,
                          symbol = it.symbol,
                          name = it.name,
                          currentPrice = it.currentPrice,
                          priceChange24h = it.priceChange24h,
                          marketCap = it.marketCap ?: ZERO_MARKET_CAP)
                    }
                call.respond(domainAssets)
              },
              onFailure = { e ->
                call.respond(mapOf("error" to "Failed to fetch assets: ${e.message}"))
              })
    }

    get("/assets/{id}") {
      kotlin
          .runCatching {
            val assetId =
                call.parameters["id"] ?: throw IllegalArgumentException("Missing asset ID")
            coinGeckoClient.getCryptoDetails(assetId)
          }
          .fold(
              onSuccess = { asset ->
                if (asset != null) {
                  val domainAsset =
                      Asset(
                          id = asset.id,
                          symbol = asset.symbol,
                          name = asset.name,
                          currentPrice = asset.currentPrice,
                          priceChange24h = asset.priceChange24h,
                          marketCap = asset.marketCap ?: ZERO_MARKET_CAP)
                  call.respond(domainAsset)
                } else {
                  call.respond(mapOf("error" to "Asset not found"))
                }
              },
              onFailure = { e ->
                call.respond(mapOf("error" to "Failed to fetch asset: ${e.message}"))
              })
    }
  }
}
