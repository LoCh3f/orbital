package io.orbital.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class HistoryApiClientTest {
  @Test
  fun `zoom request preserves coin source bounds and coverage metadata`() = runTest {
    val client =
        HttpClient(
            MockEngine { request ->
              assertEquals("/api/v1/market/history-range/bitcoin", request.url.encodedPath)
              assertEquals("binance", request.url.parameters["source"])
              assertEquals("24h", request.url.parameters["range"])
              assertEquals("1700000000", request.url.parameters["from"])
              assertEquals("1700086400", request.url.parameters["to"])
              respond(
                  """{"data":{"coinId":"bitcoin","source":"binance","quote":"USDT","pair":"BTCUSDT",
            "points":[{"timestamp":1700000000,"price":11.0}],"from":1700000000,"to":1700086400,
            "resolutionSeconds":300,"earliestAvailable":1503014399,"latestAvailable":1700000000,
            "gaps":[{"from":1700000300,"to":1700000600}],"importing":true,"binanceAvailable":true}}""",
                  headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) {
              install(ContentNegotiation) { json() }
            }
    try {
      val series =
          MarketApiClient("http://localhost", client)
              .fetchHistoryRange(
                  HistoryRequest("bitcoin", "binance", "24h", 1700000000, 1700086400))
      assertEquals("USDT", series.quote)
      assertEquals(1503014399L, series.earliestAvailable)
      assertEquals(HistoryGap(1700000300, 1700000600), series.gaps.single())
      assertEquals(true, series.importing)
    } finally {
      client.close()
    }
  }
}
