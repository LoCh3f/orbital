package io.orbital.app.ui

actual fun historyDate(epochSeconds: Long): String = utcDate(epochSeconds.toDouble())

@Suppress("UnusedParameter")
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun utcDate(seconds: Double): String =
    js("new Date(seconds * 1000).toISOString().slice(0, 10)")

actual fun historyTime(epochSeconds: Long): String = utcTime(epochSeconds.toDouble())

@Suppress("UnusedParameter")
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun utcTime(seconds: Double): String =
    js("new Date(seconds * 1000).toISOString().replace('.000Z', 'Z')")
