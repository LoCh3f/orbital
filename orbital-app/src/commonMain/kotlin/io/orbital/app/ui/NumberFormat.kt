package io.orbital.app.ui

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

private const val TRILLION = 1_000_000_000_000.0
private const val BILLION = 1_000_000_000.0
private const val MILLION = 1_000_000.0
private const val THOUSANDS_GROUP_SIZE = 3

// java.util.Formatter-backed String.format is JVM-only, so numbers are formatted
// manually here to keep this shared across the desktop and wasmJs targets.
private fun Double.toFixedString(decimals: Int): String {
  val factor = 10.0.pow(decimals)
  val roundedTotal = (abs(this) * factor).roundToLong()
  val factorLong = factor.toLong()
  val wholePart = roundedTotal / factorLong
  val fracPart = (roundedTotal % factorLong).toString().padStart(decimals, '0')
  val sign = if (this < 0) "-" else ""
  return "$sign$wholePart.$fracPart"
}

private fun groupThousands(value: Long): String {
  val digits = value.toString()
  val grouped = StringBuilder()
  for ((index, digit) in digits.withIndex()) {
    val remaining = digits.length - index
    if (index > 0 && remaining % THOUSANDS_GROUP_SIZE == 0) grouped.append(',')
    grouped.append(digit)
  }
  return grouped.toString()
}

fun formatUsd(value: Double): String =
    when {
      !value.isFinite() -> "Unavailable"
      value != 0.0 && abs(value) < MIN_PRICE_UNIT ->
          if (value > 0) "<$0.00000001" else ">-$0.00000001"
      abs(value) >= MAX_FIXED_PRICE -> "$" + value.toString()
      else -> formatFixedUsd(value)
    }

private fun formatFixedUsd(value: Double): String {
  val decimals = if (value != 0.0 && abs(value) < SMALL_PRICE_THRESHOLD) SMALL_PRICE_DECIMALS else 2
  val fixed = value.toFixedString(decimals)
  val negative = fixed.startsWith("-")
  val unsigned = if (negative) fixed.substring(1) else fixed
  val dotIndex = unsigned.indexOf('.')
  val wholePart = groupThousands(unsigned.substring(0, dotIndex).toLong())
  val fracPart = unsigned.substring(dotIndex + 1)
  return (if (negative) "-$" else "$") + "$wholePart.$fracPart"
}

fun formatPercent(value: Double): String {
  val fixed = value.toFixedString(2)
  return if (value >= 0) "+$fixed%" else "$fixed%"
}

fun formatCompactUsd(value: Double): String {
  val magnitude = abs(value)
  return when {
    magnitude >= TRILLION -> "$" + (value / TRILLION).toFixedString(2) + "T"
    magnitude >= BILLION -> "$" + (value / BILLION).toFixedString(2) + "B"
    magnitude >= MILLION -> "$" + (value / MILLION).toFixedString(2) + "M"
    else -> formatUsd(value)
  }
}

private const val SMALL_PRICE_THRESHOLD = 0.01
private const val SMALL_PRICE_DECIMALS = 8

private const val MIN_PRICE_UNIT = 0.00000001
private const val MAX_FIXED_PRICE = 1_000_000_000_000_000.0
