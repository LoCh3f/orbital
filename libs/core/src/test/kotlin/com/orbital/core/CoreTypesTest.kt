package com.orbital.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreTypesTest {
  @Test
  fun `date time utils round trip`() {
    val instant = Instant.parse("2024-01-01T00:00:00Z")
    assertEquals(instant, DateTimeUtils.parse(DateTimeUtils.format(instant)))
  }
}
