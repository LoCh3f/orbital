package io.orbital.app.ui

import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertTrue

class CoinLogoTest {
  @Test
  fun `logoCache is backed by a thread-safe map`() {
    assertTrue(logoCache is ConcurrentHashMap<String, *>)
  }
}
