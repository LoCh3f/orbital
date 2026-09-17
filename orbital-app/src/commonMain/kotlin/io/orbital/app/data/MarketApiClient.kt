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
}

@Serializable private data class MarketPricesResponse(val data: List<MarketPrice>)
