package io.orbital.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  ComposeViewport(viewportContainerId = "ComposeTarget") {
    if (IS_DEMO_BUILD) orbitalMarketDemoApp() else orbitalApp()
  }
}
