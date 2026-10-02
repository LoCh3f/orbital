package io.orbital.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orbital.app.data.CoinGeckoMarketApiClient
import io.orbital.app.ui.ORBITAL_DARK_COLOR_SCHEME
import io.orbital.app.ui.marketSection
import io.orbital.app.ui.orbitalLogoMark

private val LOGO_SIZE = 28.dp

/**
 * Market-only, backend-free entry point built for the public GitHub Pages demo: fetches live data
 * straight from CoinGecko's public `/coins/markets` endpoint (no API key, no gateway) via
 * [CoinGeckoMarketApiClient], and has no News section since there's no backend to serve it from.
 * Built instead of [orbitalApp] when the module is compiled with `-PorbitalDemoBuild=true` (see
 * `build.gradle.kts` and `wasmJsMain/Main.kt`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun orbitalMarketDemoApp() {
  val marketApiClient = remember { CoinGeckoMarketApiClient() }

  MaterialTheme(colorScheme = ORBITAL_DARK_COLOR_SCHEME) {
    Scaffold(topBar = { orbitalDemoTopBar() }) { padding ->
      Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        marketSection(fetchMarketPrices = marketApiClient::fetchMarketPrices)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun orbitalDemoTopBar() {
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
              "  /  Demo · live via CoinGecko",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 16.sp)
        }
      },
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
              titleContentColor = MaterialTheme.colorScheme.onSurface))
}
