package io.orbital.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.orbital.app.data.HistoryRequest
import io.orbital.app.data.HistorySeries
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private val HISTORY_RANGES =
    listOf("24H" to "24h", "7D" to "7d", "1M" to "1m", "1Y" to "1y", "All available" to "all")

private class HistoryViewState {
  var range by mutableStateOf("all")
  var source by mutableStateOf("auto")
  var viewport by mutableStateOf<Pair<Long, Long>?>(null)
  var series by mutableStateOf<HistorySeries?>(null)
  var error by mutableStateOf<String?>(null)
  var retry by mutableStateOf(0)

  fun requestWindow(from: Long, to: Long) {
    val data = series ?: return
    val start = from.coerceAtLeast(0)
    val end = to.coerceAtMost(data.to.coerceAtLeast(data.latestAvailable ?: data.to))
    val changed =
        abs(start - data.from) > data.resolutionSeconds ||
            abs(end - data.to) > data.resolutionSeconds
    if (end - start >= MIN_WINDOW_SECONDS && changed) viewport = start to end
  }
}

/** Shared desktop/web range and coverage state. Obsolete requests cancel when selection changes. */
@Composable
fun historyExplorer(coinId: String, load: suspend (HistoryRequest) -> HistorySeries) {
  val state = remember(coinId) { HistoryViewState() }
  LaunchedEffect(coinId, state.source, state.range, state.viewport, state.retry) {
    loadHistory(coinId, state, load)
  }
  Column(
      Modifier.fillMaxWidth().padding(top = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)) {
        historyControls(state)
        historyContent(state)
      }
}

@Suppress(
    "TooGenericExceptionCaught",
    "SwallowedException") // Render transport and decoding failures at the UI boundary.
private suspend fun loadHistory(
    coinId: String,
    state: HistoryViewState,
    load: suspend (HistoryRequest) -> HistorySeries
) {
  state.error = null
  state.series = null
  try {
    do {
      val next =
          load(
              HistoryRequest(
                  coinId, state.source, state.range, state.viewport?.first, state.viewport?.second))
      state.series = next
      if (next.importing) delay(HISTORY_POLL_MS)
    } while (state.series?.importing == true)
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (failure: Exception) {
    state.error = "History unavailable. Please retry."
  }
}

@Composable
private fun historyControls(state: HistoryViewState) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    items(HISTORY_RANGES) { (label, key) ->
      FilterChip(
          selected = state.viewport == null && state.range == key,
          onClick = {
            state.range = key
            state.viewport = null
          },
          label = { Text(label) })
    }
  }
  LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    item {
      FilterChip(
          selected = state.series?.source == "binance" || state.source == "binance",
          enabled = state.series?.binanceAvailable == true,
          onClick = {
            state.source = "binance"
            state.viewport = null
          },
          label = { Text("Binance archive · USDT") })
    }
    item {
      FilterChip(
          selected = state.series?.source == "coingecko" || state.source == "coingecko",
          onClick = {
            state.source = "coingecko"
            state.viewport = null
          },
          label = { Text("CoinGecko · USD") })
    }
  }
}

@Composable
private fun historyContent(state: HistoryViewState) {
  val data = state.series
  when {
    state.error != null -> {
      Text(state.error.orEmpty())
      Button(onClick = { state.retry++ }) { Text("Retry history") }
    }
    data == null -> {
      LinearProgressIndicator(Modifier.fillMaxWidth())
      Text("Loading history…")
    }
    else -> {
      coverageText(data)
      when {
        data.points.size >= 2 -> loadedHistory(data, state)
        data.importing -> Text("Downloading archive data for this range…")
        data.points.size == 1 ->
            Text("One observation is available; more are needed to draw the chart.")
        else -> Text("No observations in this range.")
      }
    }
  }
}

@Composable
private fun loadedHistory(data: HistorySeries, state: HistoryViewState) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(
        onClick = {
          val quarter = (data.to - data.from) / 4
          state.requestWindow(data.from + quarter, data.to - quarter)
        }) {
          Text("Zoom in")
        }
    OutlinedButton(
        onClick = {
          val half = (data.to - data.from) / 2
          state.viewport = (data.from - half).coerceAtLeast(0) to data.to
        }) {
          Text("Zoom out")
        }
  }
  if (state.viewport != null)
      Text("Custom range · ${historyTime(data.from)} – ${historyTime(data.to)}")
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    priceChart(
        Modifier,
        data.points.map { it.price },
        data.points.last().price >= data.points.first().price,
        DpSize(maxWidth, HISTORY_CHART_HEIGHT),
        true,
        data.points.map { it.timestamp },
        ChartInteraction(data.gaps, data.from, data.to, state::requestWindow))
  }
  Text(
      "${data.points.size} observations · ${data.quote} · ${intervalLabel(data.resolutionSeconds)} detail")
}

@Composable
private fun coverageText(data: HistorySeries) {
  Text(
      if (data.source == "binance") "${data.pair} · Binance closing prices in ${data.quote}"
      else "CoinGecko aggregated prices · USD")
  val progress = if (data.importing) " (loaded so far)" else ""
  val earliest = data.earliestAvailable?.let(::historyDate) ?: "not loaded yet"
  Text("Earliest available$progress: $earliest")
  data.latestAvailable?.let { Text("Latest available: ${historyDate(it)} UTC") }
  data.notice?.let { Text(it) }
  if (data.importing) {
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Text("Importing available history. Coverage expands as archive files arrive.")
  }
  Text(
      if (data.points.isEmpty()) "No coverage loaded for this range."
      else if (data.gaps.isEmpty()) "No gaps detected at this resolution in loaded data."
      else "${data.gaps.size} gap(s) in loaded coverage:")
  data.gaps.take(MAX_GAP_LABELS).forEach { Text("${historyTime(it.from)} – ${historyTime(it.to)}") }
  if (data.source == "binance")
      Text("Source: Binance Vision · CC BY-NC-SA 4.0 · non-commercial dataset")
}

internal fun intervalLabel(seconds: Long): String =
    when {
      seconds >= SECONDS_PER_DAY -> "${seconds / SECONDS_PER_DAY}d"
      seconds >= SECONDS_PER_HOUR -> "${seconds / SECONDS_PER_HOUR}h"
      else -> "${seconds / SECONDS_PER_MINUTE}m"
    }

expect fun historyDate(epochSeconds: Long): String

expect fun historyTime(epochSeconds: Long): String

private const val HISTORY_POLL_MS = 5000L
private const val MIN_WINDOW_SECONDS = 60L
private const val MAX_GAP_LABELS = 3
private const val SECONDS_PER_DAY = 86400L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
private val HISTORY_CHART_HEIGHT = 240.dp
