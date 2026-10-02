package io.orbital.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orbital.app.data.MarketApiClient
import io.orbital.app.data.NewsApiClient
import io.orbital.app.data.NewsItem
import io.orbital.app.data.createHttpClient
import io.orbital.app.data.defaultGatewayUrl
import io.orbital.app.ui.NewsFilters
import io.orbital.app.ui.ORBITAL_DARK_COLOR_SCHEME
import io.orbital.app.ui.UiState
import io.orbital.app.ui.marketIcon
import io.orbital.app.ui.marketSection
import io.orbital.app.ui.newsIcon
import io.orbital.app.ui.newsScreen
import io.orbital.app.ui.orbitalLogoMark
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val NEWS_REFRESH_INTERVAL_MS = 60_000L
private val LOGO_SIZE = 28.dp

private enum class Section(val label: String) {
  MARKET("Markets"),
  NEWS("News")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun orbitalApp() {
  val gatewayUrl = remember { defaultGatewayUrl() }
  val client = remember { createHttpClient() }
  val marketApiClient = remember { MarketApiClient(baseUrl = gatewayUrl, client = client) }
  val newsApiClient = remember { NewsApiClient(baseUrl = gatewayUrl, client = client) }
  val scope = rememberCoroutineScope()

  var selectedSection by remember { mutableStateOf(Section.MARKET) }
  var newsState by remember { mutableStateOf<UiState<List<NewsItem>>>(UiState.Loading) }
  var newsSearchQuery by remember { mutableStateOf("") }
  var selectedNewsCategory by remember { mutableStateOf<String?>(null) }

  suspend fun refreshNews() {
    runCatching { newsApiClient.fetchNews(selectedNewsCategory) }
        .onSuccess { newsState = UiState.Success(it) }
        .onFailure { error ->
          val previous = newsState
          newsState =
              if (previous is UiState.Success) previous
              else UiState.Error(error.message ?: "Unknown error")
        }
  }

  LaunchedEffect(selectedNewsCategory) {
    newsState = UiState.Loading
    while (isActive) {
      refreshNews()
      delay(NEWS_REFRESH_INTERVAL_MS)
    }
  }

  MaterialTheme(colorScheme = ORBITAL_DARK_COLOR_SCHEME) {
    Scaffold(topBar = { orbitalTopBar(selectedSection) { selectedSection = it } }) { padding ->
      Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        // marketSection is composed unconditionally (not inside this `when`) so its remembered
        // search text and polling state survive switching to News and back — see its doc comment.
        marketSection(
            fetchMarketPrices = marketApiClient::fetchMarketPrices,
            visible = selectedSection == Section.MARKET)
        if (selectedSection == Section.NEWS) {
          newsScreen(
              newsState,
              filters =
                  NewsFilters(
                      category = selectedNewsCategory,
                      onCategoryChange = { selectedNewsCategory = it },
                      query = newsSearchQuery,
                      onQueryChange = { newsSearchQuery = it }),
              onRefresh = { scope.launch { refreshNews() } })
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun orbitalTopBar(selectedSection: Section, onSectionSelected: (Section) -> Unit) {
  TopAppBar(
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          orbitalLogoMark(modifier = Modifier.padding(end = 8.dp), size = LOGO_SIZE)
          Text(
              "ORBITAL",
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              letterSpacing = 1.5.sp)
          Text(
              "  /  ${selectedSection.label}",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 16.sp)
        }
      },
      actions = {
        IconButton(onClick = { onSectionSelected(Section.MARKET) }) {
          Icon(
              marketIcon(),
              contentDescription = "Market",
              tint =
                  if (selectedSection == Section.MARKET) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onSectionSelected(Section.NEWS) }) {
          Icon(
              newsIcon(),
              contentDescription = "News",
              tint =
                  if (selectedSection == Section.NEWS) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
              actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant))
}
