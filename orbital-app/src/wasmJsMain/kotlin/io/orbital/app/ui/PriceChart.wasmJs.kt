package io.orbital.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlin.math.roundToInt
import kotlinx.browser.document
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLScriptElement

private val chartsLoadMutex = Mutex()
private var chartsInjected = false
private var librarySource: String? = null

private suspend fun ensureChartsInjected() {
  if (chartsInjected) return
  chartsLoadMutex.withLock {
    if (chartsInjected) return@withLock
    val source = librarySource ?: loadLightweightChartsLibrarySource().also { librarySource = it }
    injectGlobalScript(source)
    injectGlobalScript(RENDER_SPARKLINE_JS)
    chartsInjected = true
  }
}

private fun injectGlobalScript(source: String) {
  val script = document.createElement("script") as HTMLScriptElement
  script.text = source
  document.head?.appendChild(script)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun priceChart(
    modifier: Modifier,
    points: List<Double>,
    positive: Boolean,
    size: DpSize,
    interactive: Boolean
) {
  var ready by remember { mutableStateOf(chartsInjected) }

  LaunchedEffect(Unit) {
    ensureChartsInjected()
    ready = true
  }

  if (!ready || points.size < 2) return

  // Compose-for-web positions interop elements in CSS pixels (1dp == 1px), independent of
  // devicePixelRatio — using LocalDensity.current here (meant for canvas backing-store scaling)
  // previously produced a chart sized in physical pixels, overflowing its CSS-sized container.
  val widthPx = size.width.value.roundToInt()
  val heightPx = size.height.value.roundToInt()
  val valuesCsv = points.joinToString(",")
  HtmlElementView(
      factory = {
        (document.createElement("div") as HTMLDivElement).apply {
          style.width = "100%"
          style.height = "100%"
        }
      },
      modifier = modifier.size(size),
      update = { element ->
        callRenderSparkline(element, valuesCsv, positive, widthPx, heightPx, interactive)
      })
}

// The parameters are referenced by name inside the js() snippet below, which detekt's static
// analysis can't see.
@Suppress("UnusedParameter", "LongParameterList")
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun callRenderSparkline(
    element: HTMLElement,
    valuesCsv: String,
    positive: Boolean,
    width: Int,
    height: Int,
    interactive: Boolean
) {
  js("window.renderSparkline(element, valuesCsv, positive, width, height, interactive)")
}
