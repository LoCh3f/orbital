package io.orbital.app.ui

import java.time.Instant
import java.time.ZoneOffset

actual fun historyDate(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC).toLocalDate().toString()

actual fun historyTime(epochSeconds: Long): String = Instant.ofEpochSecond(epochSeconds).toString()
