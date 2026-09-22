package io.orbital.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.orbital.app.data.MarketPrice
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

private const val POSITIVE_CHANGE_ARGB = 0xFF2E7D32L
private const val NEGATIVE_CHANGE_ARGB = 0xFFC62828L
private val POSITIVE_CHANGE_COLOR = Color(POSITIVE_CHANGE_ARGB)
private val NEGATIVE_CHANGE_COLOR = Color(NEGATIVE_CHANGE_ARGB)
private const val TRILLION = 1_000_000_000_000.0
private const val BILLION = 1_000_000_000.0
private const val MILLION = 1_000_000.0
private const val THOUSANDS_GROUP_SIZE = 3

@Composable
fun marketScreen(
    state: UiState<List<MarketPrice>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        label = { Text("Search coins") },
        singleLine = true)

    when (state) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Unable to load market data: ${state.message}")
              Button(onClick = onRefresh) { Text("Retry") }
            }
      }
      is UiState.Success -> {
        val filtered = filterCoins(state.data, searchQuery)
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
              items(filtered) { coin -> coinPriceRow(coin) }
            }
      }
    }
  }
}

private fun filterCoins(coins: List<MarketPrice>, query: String): List<MarketPrice> {
  if (query.isBlank()) return coins
  val needle = query.trim().lowercase()
  return coins.filter {
    it.symbol.lowercase().contains(needle) || it.name.lowercase().contains(needle)
  }
}

@Composable
private fun coinPriceRow(coin: MarketPrice) {
  var expanded by remember { mutableStateOf(false) }
  Card(
      modifier =
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
            expanded = !expanded
          }) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                  Text(coin.symbol)
                  Text(coin.name)
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(formatUsd(coin.currentPriceUsd))
                  Text(
                      formatPercent(coin.priceChangePercent24h),
                      color =
                          if (coin.priceChangePercent24h >= 0) POSITIVE_CHANGE_COLOR
                          else NEGATIVE_CHANGE_COLOR)
                  Text(formatCompactUsd(coin.marketCapUsd))
                }
              }
          if (expanded) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("24h volume: ${formatCompactUsd(coin.volume24hUsd)}")
                  Text("Updated: ${coin.lastUpdated}")
                }
          }
        }
      }
}

// java.util.Formatter-backed String.format is JVM-only, so numbers are formatted
// manually here to keep this shared across the desktop and wasmJs targets.
private fun Double.toFixedString(decimals: Int): String {
  val factor = 10.0.pow(decimals)
  val roundedTotal = (abs(this) * factor).roundToLong()
  val factorLong = factor.toLong()
  val wholePart = roundedTotal / factorLong
  val fracPart = (roundedTotal % factorLong).toString().padStart(decimals, '0')
  val sign = if (this < 0) "-" else ""
  return "$sign$wholePart.$fracPart"
}

private fun groupThousands(value: Long): String {
  val digits = value.toString()
  val grouped = StringBuilder()
  for ((index, digit) in digits.withIndex()) {
    val remaining = digits.length - index
    if (index > 0 && remaining % THOUSANDS_GROUP_SIZE == 0) grouped.append(',')
    grouped.append(digit)
  }
  return grouped.toString()
}

private fun formatUsd(value: Double): String {
  val fixed = value.toFixedString(2)
  val negative = fixed.startsWith("-")
  val unsigned = if (negative) fixed.substring(1) else fixed
  val dotIndex = unsigned.indexOf('.')
  val wholePart = groupThousands(unsigned.substring(0, dotIndex).toLong())
  val fracPart = unsigned.substring(dotIndex + 1)
  return (if (negative) "-$" else "$") + "$wholePart.$fracPart"
}

private fun formatPercent(value: Double): String {
  val fixed = value.toFixedString(2)
  return if (value >= 0) "+$fixed%" else "$fixed%"
}

private fun formatCompactUsd(value: Double): String {
  val magnitude = abs(value)
  return when {
    magnitude >= TRILLION -> "$" + (value / TRILLION).toFixedString(2) + "T"
    magnitude >= BILLION -> "$" + (value / BILLION).toFixedString(2) + "B"
    magnitude >= MILLION -> "$" + (value / MILLION).toFixedString(2) + "M"
    else -> formatUsd(value)
  }
}
