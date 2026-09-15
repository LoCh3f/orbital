package com.orbital.core

import java.time.Instant
import java.time.format.DateTimeFormatter

object DateTimeUtils {
  private val isoFormatter = DateTimeFormatter.ISO_INSTANT

  fun format(instant: Instant): String = isoFormatter.format(instant)

  fun parse(value: String): Instant = Instant.parse(value)
}
