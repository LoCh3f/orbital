package io.orbital.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import dev.datlag.kcef.KCEF
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val kcefInitMutex = Mutex()
private var kcefInitStarted = false
private val kcefReady = CompletableDeferred<Unit>()
private var cachedLibrarySource: String? = null

private suspend fun ensureKcefInitialized() {
  kcefInitMutex.withLock {
    if (!kcefInitStarted) {
      kcefInitStarted = true
      withContext(Dispatchers.IO) {
        val installDir = File(System.getProperty("user.home"), ".orbital/kcef-bundle")
        KCEF.init(
            builder = {
              installDir(installDir)
              progress { onInitialized { kcefReady.complete(Unit) } }
              settings { cachePath = File(installDir, "cache").absolutePath }
            },
            onError = { it?.printStackTrace() },
            onRestartRequired = {})
      }
    }
  }
  kcefReady.await()
}

@Composable
actual fun priceChart(
    modifier: Modifier,
    points: List<Double>,
    positive: Boolean,
    size: DpSize,
    interactive: Boolean
) {
  var librarySource by remember { mutableStateOf(cachedLibrarySource) }
  var ready by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    if (librarySource == null) {
      librarySource = loadLightweightChartsLibrarySource().also { cachedLibrarySource = it }
    }
    ensureKcefInitialized()
    ready = true
  }

  val source = librarySource
  if (!ready || source == null || points.size < 2) return

  key(points, positive, interactive) {
    val html = sparklineHtmlDocument(source, points.joinToString(","), positive, interactive)
    val state = rememberWebViewStateWithHTMLData(data = html)
    WebView(state = state, modifier = modifier.size(size))
  }
}
