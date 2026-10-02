package com.orbital.market.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** CoinGecko's documented maximum `per_page` value for `/coins/markets`. */
private const val MAX_PAGE_SIZE = 250

/** Thin wrapper over CoinGecko's public `/coins/markets` endpoint. No API key required. */
class CoinGeckoClient(private val client: HttpClient) {
  private val baseUrl = "https://api.coingecko.com/api/v3"

  /** Fetches the top [limit] coins by market cap, in USD, with 24h price change included. */
  suspend fun getTopCryptos(limit: Int = 50): List<CoinGeckoCryptoData> {
    val response =
        client.get("$baseUrl/coins/markets") {
          parameter("vs_currency", "usd")
          parameter("order", "market_cap_desc")
          parameter("per_page", limit)
          parameter("page", 1)
          parameter("sparkline", true)
          parameter("price_change_percentage", "24h")
        }
    return response.body()
  }

  /** Fetches details for a single coin by its CoinGecko [id], or `null` if not found. */
  suspend fun getCryptoDetails(id: String): CoinGeckoCryptoData? {
    val response =
        client.get("$baseUrl/coins/markets") {
          parameter("vs_currency", "usd")
          parameter("ids", id)
          parameter("sparkline", false)
        }
    return response.body<List<CoinGeckoCryptoData>>().firstOrNull()
  }

  /** Fetches exactly the coins in [ids] (by CoinGecko id), regardless of market-cap rank. */
  suspend fun getCryptosByIds(ids: List<String>): List<CoinGeckoCryptoData> {
    if (ids.isEmpty()) return emptyList()
    val response =
        client.get("$baseUrl/coins/markets") {
          parameter("vs_currency", "usd")
          parameter("ids", ids.joinToString(","))
          parameter("per_page", ids.size.coerceAtMost(MAX_PAGE_SIZE))
          parameter("sparkline", true)
          parameter("price_change_percentage", "24h")
        }
    return response.body()
  }
}
