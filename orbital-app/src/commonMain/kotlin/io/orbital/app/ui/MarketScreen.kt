package io.orbital.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orbital.app.data.MarketPrice

private val CARD_SHAPE = RoundedCornerShape(12.dp)
private val BADGE_SHAPE = RoundedCornerShape(4.dp)
private val FIELD_SHAPE = RoundedCornerShape(12.dp)
private val AVATAR_SIZE = 36.dp
private const val AVATAR_SYMBOL_MAX_CHARS = 3
private val SPARKLINE_WIDTH = 64.dp
private val SPARKLINE_HEIGHT = 28.dp
private val DETAIL_CHART_HEIGHT = 200.dp

@Composable
fun marketScreen(
    state: UiState<List<MarketPrice>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("Search ticker or protocol...") },
        singleLine = true,
        shape = FIELD_SHAPE,
        colors = marketSearchFieldColors())

    when (state) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      }
      is UiState.Error -> {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                  "Unable to load market data: ${state.message}",
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
              Button(onClick = onRefresh) { Text("Retry") }
            }
      }
      is UiState.Success -> {
        val filtered = filterCoins(state.data, searchQuery)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(filtered) { coin -> coinPriceRow(coin) }
            }
      }
    }
  }
}

@Composable
private fun marketSearchFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant)

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
      modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
      shape = CARD_SHAPE,
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
          coinSummaryRow(coin)
          if (expanded) coinExpandedDetails(coin)
        }
      }
}

@Composable
private fun coinSummaryRow(coin: MarketPrice) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          coinAvatar(coin.symbol, coin.logoUrl)
          Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                coin.symbol,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp)
            Text(
                coin.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
          }
        }
        if (coin.sparklineIn7d.size >= 2) {
          priceChart(
              modifier = Modifier,
              points = coin.sparklineIn7d,
              positive = coin.priceChangePercent24h >= 0,
              size = DpSize(SPARKLINE_WIDTH, SPARKLINE_HEIGHT))
        }
        Column(horizontalAlignment = Alignment.End) {
          Text(
              formatUsd(coin.currentPriceUsd),
              color = MaterialTheme.colorScheme.onSurface,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.SemiBold,
              fontSize = 14.sp)
          deltaBadge(coin.priceChangePercent24h)
          Text(
              formatCompactUsd(coin.marketCapUsd),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp)
        }
      }
}

@Composable
private fun coinExpandedDetails(coin: MarketPrice) {
  HorizontalDivider(
      modifier = Modifier.padding(vertical = 8.dp),
      color = MaterialTheme.colorScheme.outlineVariant)
  if (coin.sparklineIn7d.size >= 2) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
      priceChart(
          modifier = Modifier,
          points = coin.sparklineIn7d,
          positive = coin.priceChangePercent24h >= 0,
          size = DpSize(maxWidth, DETAIL_CHART_HEIGHT),
          interactive = true)
    }
  }
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
        "24h volume: ${formatCompactUsd(coin.volume24hUsd)}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp)
    Text(
        "Updated: ${coin.lastUpdated}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp)
  }
}

@Composable
private fun coinAvatar(symbol: String, logoUrl: String) {
  var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
  Box(
      modifier =
          Modifier.size(AVATAR_SIZE)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceContainer),
      contentAlignment = Alignment.Center) {
        if (!logoLoaded) {
          Text(
              symbol.take(AVATAR_SYMBOL_MAX_CHARS),
              color = MaterialTheme.colorScheme.primary,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp)
        }
        if (logoUrl.isNotBlank()) {
          coinLogoImage(
              modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape),
              url = logoUrl,
              onResult = { success -> logoLoaded = success })
        }
      }
}

@Composable
private fun deltaBadge(changePercent: Double) {
  val positive = changePercent >= 0
  val containerColor =
      if (positive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
      else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
  val contentColor =
      if (positive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
  Row(
      modifier =
          Modifier.padding(top = 2.dp, bottom = 2.dp)
              .clip(BADGE_SHAPE)
              .background(containerColor)
              .padding(horizontal = 4.dp, vertical = 1.dp),
      verticalAlignment = Alignment.CenterVertically) {
        val arrow = if (positive) "▲" else "▼"
        Text(
            "$arrow ${formatPercent(changePercent)}",
            color = contentColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp)
      }
}
