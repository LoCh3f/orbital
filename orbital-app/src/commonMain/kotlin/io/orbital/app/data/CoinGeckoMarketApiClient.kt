package io.orbital.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3"
private const val TOP_N_COINS = 100

/**
 * Fetches market data directly from CoinGecko's public `/coins/markets` endpoint — no API key or
 * backend required (CoinGecko serves this endpoint with `Access-Control-Allow-Origin: *`, so it's
 * callable straight from a browser). Used by the backend-free demo build deployed to GitHub Pages;
 * the full app uses [MarketApiClient] against the gateway instead.
 */
class CoinGeckoMarketApiClient(private val client: HttpClient = createHttpClient()) {
  suspend fun fetchMarketPrices(): List<MarketPrice> =
      client
          .get("$COINGECKO_BASE_URL/coins/markets") {
            parameter("vs_currency", "usd")
            parameter("order", "market_cap_desc")
            parameter("per_page", TOP_N_COINS)
            parameter("page", 1)
            parameter("sparkline", true)
            parameter("price_change_percentage", "24h")
          }
          .body<List<CoinGeckoDto>>()
          .map { it.toMarketPrice() }
}

// Mirrors the subset of CoinGecko's `/coins/markets` wire shape this client needs. See
// com.orbital.market.api.CoinGeckoCryptoData (market-service) for the server-side equivalent —
// kept separate because orbital-app has no dependency on that JVM-only module.
@Serializable
private data class CoinGeckoDto(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String,
    @SerialName("current_price") val currentPrice: Double,
    @SerialName("market_cap") val marketCap: Long,
    @SerialName("total_volume") val totalVolume: Long,
    @SerialName("price_change_percentage_24h") val priceChangePercentage24h: Double? = null,
    @SerialName("last_updated") val lastUpdated: String,
    @SerialName("sparkline_in_7d") val sparklineIn7d: CoinGeckoSparklineDto? = null
)

@Serializable private data class CoinGeckoSparklineDto(val price: List<Double> = emptyList())

private fun CoinGeckoDto.toMarketPrice() =
    MarketPrice(
        coinId = id,
        symbol = symbol.uppercase(),
        name = name,
        currentPriceUsd = currentPrice,
        marketCapUsd = marketCap.toDouble(),
        volume24hUsd = totalVolume.toDouble(),
        priceChangePercent24h = priceChangePercentage24h ?: 0.0,
        lastUpdated = lastUpdated,
        sparklineIn7d = sparklineIn7d?.price.orEmpty(),
        logoUrl = image)
