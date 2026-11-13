package io.orbital.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

class MarketApiClient(
    private val baseUrl: String = defaultGatewayUrl(),
    private val client: HttpClient = createHttpClient()
) {
  suspend fun fetchMarketPrices(): List<MarketPrice> =
      client.get("$baseUrl/api/v1/market/prices").checked().body<MarketPricesResponse>().data

  /**
   * Our own ingested price history for one coin, oldest first — not CoinGecko's 7-day sparkline.
   */
  suspend fun fetchPriceHistory(coinId: String): List<HistoryPoint> =
      client
          .get("$baseUrl/api/v1/market/history-points/$coinId")
          .checked()
          .body<PriceHistoryResponse>()
          .data

  suspend fun fetchHistoryRange(request: HistoryRequest): HistorySeries =
      client
          .get("$baseUrl/api/v1/market/history-range/${request.coinId}") {
            parameter("source", request.source)
            parameter("range", request.range)
            request.from?.let { parameter("from", it) }
            request.to?.let { parameter("to", it) }
          }
          .checked()
          .body<HistorySeriesResponse>()
          .data
}

@Serializable private data class MarketPricesResponse(val data: List<MarketPrice>)

@Serializable private data class PriceHistoryResponse(val data: List<HistoryPoint>)

@Serializable data class HistoryPoint(val timestamp: Long, val priceUsd: Double)

@Serializable private data class HistorySeriesResponse(val data: HistorySeries)
