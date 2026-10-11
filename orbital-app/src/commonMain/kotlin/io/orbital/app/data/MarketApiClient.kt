package io.orbital.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

class MarketApiClient(
    private val baseUrl: String = defaultGatewayUrl(),
    private val client: HttpClient = createHttpClient()
) {
  suspend fun fetchMarketPrices(): List<MarketPrice> =
      client.get("$baseUrl/api/v1/market/prices").body<MarketPricesResponse>().data

  /** Our own ingested price history for one coin, oldest first — not CoinGecko's 7-day sparkline. */
  suspend fun fetchPriceHistory(coinId: String): List<Double> =
      client.get("$baseUrl/api/v1/market/history/$coinId").body<PriceHistoryResponse>().data
}

@Serializable private data class MarketPricesResponse(val data: List<MarketPrice>)

@Serializable private data class PriceHistoryResponse(val data: List<Double>)
