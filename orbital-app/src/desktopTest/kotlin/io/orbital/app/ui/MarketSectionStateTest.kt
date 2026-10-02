package io.orbital.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.orbital.app.data.MarketPrice
import kotlin.test.Test

/**
 * Regression test for a Compose composition-lifetime bug: if [marketSection] is only composed while
 * a tab/branch is selected (e.g. inside a `when` branch), its `remember`ed search text and polling
 * state are torn down and recreated every time the tab is switched away and back — the exact bug
 * `App.kt`'s `orbitalApp()` must not reintroduce.
 */
class MarketSectionStateTest {
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun `marketSection preserves search text across a visibility toggle`() = runComposeUiTest {
    var visible by mutableStateOf(true)

    setContent {
      marketSection(fetchMarketPrices = { emptyList<MarketPrice>() }, visible = visible)
    }

    waitForIdle()
    onNodeWithText("Search ticker or protocol...").performTextInput("btc")
    waitForIdle()
    onNodeWithText("btc").assertExists()

    visible = false
    waitForIdle()
    visible = true
    waitForIdle()

    onNodeWithText("btc").assertExists()
  }
}
