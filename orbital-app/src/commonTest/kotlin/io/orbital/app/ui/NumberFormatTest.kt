package io.orbital.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {
  @Test
  fun smallPricesRetainPrecision() {
    assertEquals("$0.00001234", formatUsd(0.00001234))
    assertEquals("<$0.00000001", formatUsd(0.00000000001))
    assertEquals("$0.00", formatUsd(0.0))
    assertEquals("$72,300.00", formatUsd(72300.0))
    assertEquals("Unavailable", formatUsd(Double.NaN))
  }
}
