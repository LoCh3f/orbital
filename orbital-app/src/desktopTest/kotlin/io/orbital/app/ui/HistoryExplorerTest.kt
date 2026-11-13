package io.orbital.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.orbital.app.data.HistoryRequest
import io.orbital.app.data.HistorySeries
import io.orbital.app.data.HistoryValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

@OptIn(ExperimentalTestApi::class)
class HistoryExplorerTest {
  private fun fixture(request: HistoryRequest): HistorySeries =
      HistorySeries(
          coinId = request.coinId,
          source = if (request.source == "coingecko") "coingecko" else "binance",
          quote = if (request.source == "coingecko") "USD" else "USDT",
          pair = "BTCUSDT",
          points = listOf(HistoryValue(1000000, 10.0), HistoryValue(2000000, 20.0)),
          from = request.from ?: 1000000,
          to = request.to ?: 2000000,
          resolutionSeconds = 300,
          earliestAvailable = 1000000,
          latestAvailable = 2000000,
          binanceAvailable = true)

  @Test
  fun `range source and zoom controls request only the expanded coin`() = runComposeUiTest {
    val requests = mutableListOf<HistoryRequest>()
    setContent {
      historyExplorer("bitcoin") { request ->
        requests += request
        fixture(request)
      }
    }
    waitForIdle()
    assertEquals("all", requests.last().range)
    onNodeWithText("1M").performClick()
    waitForIdle()
    assertEquals("1m", requests.last().range)
    onNodeWithText("Zoom in").performClick()
    waitForIdle()
    assertEquals(1250000L, requests.last().from)
    assertEquals(1750000L, requests.last().to)
    onNodeWithText("CoinGecko · USD").performClick()
    waitForIdle()
    assertEquals("coingecko", requests.last().source)
    assertEquals(null, requests.last().from)
    assertTrue(requests.all { it.coinId == "bitcoin" })
    onNodeWithText("CoinGecko aggregated prices · USD").assertExists()
  }

  @Test
  fun `obsolete range requests cancel before a new range is displayed`() = runComposeUiTest {
    var cancelled = false
    setContent {
      historyExplorer("bitcoin") { request ->
        if (request.range == "24h") {
          try {
            awaitCancellation()
          } catch (failure: CancellationException) {
            cancelled = true
            throw failure
          }
        }
        fixture(request)
      }
    }
    waitForIdle()
    onNodeWithText("24H").performClick()
    waitForIdle()
    onNodeWithText("7D").performClick()
    waitForIdle()
    assertTrue(cancelled)
    onNodeWithText("BTCUSDT · Binance closing prices in USDT").assertExists()
  }

  @Test
  fun `unlisted coins cannot select Binance and empty history explains coverage`() =
      runComposeUiTest {
        setContent {
          historyExplorer("unlisted-coin") { request ->
            fixture(request)
                .copy(
                    source = "coingecko",
                    quote = "USD",
                    pair = null,
                    points = emptyList(),
                    earliestAvailable = null,
                    latestAvailable = null,
                    binanceAvailable = false,
                    notice =
                        "No verified Binance listing. Showing available CoinGecko USD history.")
          }
        }
        waitForIdle()
        onNodeWithText("Binance archive · USDT").assertIsNotEnabled()
        onNodeWithText("No observations in this range.").assertExists()
        onNodeWithText("Earliest available: not loaded yet").assertExists()
      }
}
