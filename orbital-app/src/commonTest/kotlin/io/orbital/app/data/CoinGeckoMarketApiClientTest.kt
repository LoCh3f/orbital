package io.orbital.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

private const val VALID_COINGECKO_JSON =
    """[{"id":"bitcoin","symbol":"btc","name":"Bitcoin","image":"https://example.com/btc.png",
    "current_price":65000.0,"market_cap":1000000,"total_volume":500000,
    "price_change_percentage_24h":2.35,"last_updated":"2024-01-01T00:00:00Z",
    "sparkline_in_7d":{"price":[64000.0,64500.0,65000.0]}}]"""

private fun clientReturning(status: HttpStatusCode, body: String) =
    CoinGeckoMarketApiClient(
        HttpClient(
            MockEngine { _ ->
              respond(
                  content = body,
                  status = status,
                  headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) {
              install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            })

class CoinGeckoMarketApiClientTest {
  @Test
  fun `fetchMarketPrices maps CoinGecko's wire shape to MarketPrice`() = runTest {
    val prices = clientReturning(HttpStatusCode.OK, VALID_COINGECKO_JSON).fetchMarketPrices()

    assertEquals(1, prices.size)
    val btc = prices.first()
    assertEquals("bitcoin", btc.coinId)
    assertEquals("BTC", btc.symbol)
    assertEquals("Bitcoin", btc.name)
    assertEquals(65000.0, btc.currentPriceUsd)
    assertEquals(1000000.0, btc.marketCapUsd)
    assertEquals(500000.0, btc.volume24hUsd)
    assertEquals(2.35, btc.priceChangePercent24h)
    assertEquals("https://example.com/btc.png", btc.logoUrl)
    assertEquals(listOf(64000.0, 64500.0, 65000.0), btc.sparklineIn7d)
  }

  @Test
  fun `fetchMarketPrices defaults missing 24h change and sparkline fields`() = runTest {
    val jsonWithoutOptionalFields =
        """[{"id":"bitcoin","symbol":"btc","name":"Bitcoin","image":"https://example.com/btc.png",
        "current_price":65000.0,"market_cap":1000000,"total_volume":500000,
        "last_updated":"2024-01-01T00:00:00Z"}]"""

    val btc =
        clientReturning(HttpStatusCode.OK, jsonWithoutOptionalFields).fetchMarketPrices().first()

    assertEquals(0.0, btc.priceChangePercent24h)
    assertEquals(emptyList(), btc.sparklineIn7d)
  }

  @Test
  fun `fetchMarketPrices accepts a fractional total_volume value`() = runTest {
    // CoinGecko sends total_volume as a float (e.g. 0.0) for very low-volume coins instead of an
    // integer — this reproduces a real production error where that broke strict Long decoding.
    val jsonWithFractionalVolume =
        """[{"id":"bitcoin","symbol":"btc","name":"Bitcoin","image":"https://example.com/btc.png",
        "current_price":65000.0,"market_cap":1000000,"total_volume":0.0,
        "last_updated":"2024-01-01T00:00:00Z"}]"""

    val btc =
        clientReturning(HttpStatusCode.OK, jsonWithFractionalVolume).fetchMarketPrices().first()

    assertEquals(0.0, btc.volume24hUsd)
  }

  @Test
  fun `fetchMarketPrices accepts a fractional market_cap value`() = runTest {
    // Same wire-format risk as total_volume above: CoinGecko can send market_cap as a float too.
    val jsonWithFractionalMarketCap =
        """[{"id":"bitcoin","symbol":"btc","name":"Bitcoin","image":"https://example.com/btc.png",
        "current_price":65000.0,"market_cap":2406243294.5,"total_volume":500000,
        "last_updated":"2024-01-01T00:00:00Z"}]"""

    val btc =
        clientReturning(HttpStatusCode.OK, jsonWithFractionalMarketCap).fetchMarketPrices().first()

    assertEquals(2406243294.5, btc.marketCapUsd)
  }

  @Test
  fun `fetchMarketPrices throws on a non-2xx response even with a well-formed body`() = runTest {
    // "[]" is a valid, empty CoinGecko response shape — this proves the client checks the HTTP
    // status itself rather than relying on a malformed body to accidentally fail deserialization.
    assertFailsWith<Exception> {
      clientReturning(HttpStatusCode.TooManyRequests, "[]").fetchMarketPrices()
    }
  }
}
