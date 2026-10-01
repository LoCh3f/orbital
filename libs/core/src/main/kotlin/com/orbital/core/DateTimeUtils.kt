package com.orbital.core

import java.time.Instant
import java.time.format.DateTimeFormatter

/** ISO-8601 helpers used wherever a timestamp crosses the wire as a plain string. */
object DateTimeUtils {
  private val isoFormatter = DateTimeFormatter.ISO_INSTANT

  /** Formats [instant] as an ISO-8601 string, e.g. `2024-01-01T00:00:00Z`. */
  fun format(instant: Instant): String = isoFormatter.format(instant)

  /** Parses an ISO-8601 string (as produced by [format]) back into an [Instant]. */
  fun parse(value: String): Instant = Instant.parse(value)
}
