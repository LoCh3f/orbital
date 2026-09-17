package io.orbital.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.orbital.app.data.MarketApiClient
import io.orbital.app.data.MarketPrice
import io.orbital.app.data.NewsApiClient
import io.orbital.app.data.NewsItem
import io.orbital.app.data.createHttpClient
import io.orbital.app.data.defaultGatewayUrl
import io.orbital.app.ui.UiState
import io.orbital.app.ui.marketScreen
import io.orbital.app.ui.newsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MARKET_REFRESH_INTERVAL_MS = 30_000L
private const val NEWS_REFRESH_INTERVAL_MS = 60_000L

@Composable
fun orbitalApp() {
  val gatewayUrl = remember { defaultGatewayUrl() }
  val client = remember { createHttpClient() }
  val marketApiClient = remember { MarketApiClient(baseUrl = gatewayUrl, client = client) }
  val newsApiClient = remember { NewsApiClient(baseUrl = gatewayUrl, client = client) }
  val scope = rememberCoroutineScope()

  var marketState by remember { mutableStateOf<UiState<List<MarketPrice>>>(UiState.Loading) }
  var newsState by remember { mutableStateOf<UiState<List<NewsItem>>>(UiState.Loading) }

  suspend fun refreshMarket() {
    runCatching { marketApiClient.fetchMarketPrices() }
        .onSuccess { marketState = UiState.Success(it) }
        .onFailure { error ->
          val previous = marketState
          marketState =
              if (previous is UiState.Success) previous
              else UiState.Error(error.message ?: "Unknown error")
        }
  }

  suspend fun refreshNews() {
    runCatching { newsApiClient.fetchNews() }
        .onSuccess { newsState = UiState.Success(it) }
        .onFailure { error ->
          val previous = newsState
          newsState =
              if (previous is UiState.Success) previous
              else UiState.Error(error.message ?: "Unknown error")
        }
  }

  LaunchedEffect(Unit) {
    while (isActive) {
      refreshMarket()
      delay(MARKET_REFRESH_INTERVAL_MS)
    }
  }

  LaunchedEffect(Unit) {
    while (isActive) {
      refreshNews()
      delay(NEWS_REFRESH_INTERVAL_MS)
    }
  }

  MaterialTheme {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
      Text("Orbital")
      Text("Market:")
      Column(modifier = Modifier.fillMaxWidth().height(320.dp)) {
        marketScreen(marketState, onRefresh = { scope.launch { refreshMarket() } })
      }
      Text("\nNews:")
      Column(modifier = Modifier.fillMaxWidth().height(320.dp)) {
        newsScreen(newsState, onRefresh = { scope.launch { refreshNews() } })
      }
    }
  }
}
