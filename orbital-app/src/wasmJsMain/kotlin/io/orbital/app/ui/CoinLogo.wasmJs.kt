package io.orbital.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLImageElement

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun coinLogoImage(modifier: Modifier, url: String, onResult: (Boolean) -> Unit) {
  val currentOnResult = rememberUpdatedState(onResult)

  HtmlElementView(
      factory = {
        (document.createElement("img") as HTMLImageElement).apply {
          style.width = "100%"
          style.height = "100%"
          style.objectFit = "cover"
          style.borderRadius = "50%"
          addEventListener("load", { currentOnResult.value(true) })
          // No CORS headers on CoinGecko's asset CDN, so a broken/blocked image is expected
          // sometimes — hide it on failure rather than showing the browser's broken-image icon,
          // letting the caller's fallback (drawn separately) show through.
          addEventListener(
              "error",
              {
                style.display = "none"
                currentOnResult.value(false)
              })
        }
      },
      modifier = modifier,
      update = {
        it.style.display = ""
        it.src = url
      })
}
