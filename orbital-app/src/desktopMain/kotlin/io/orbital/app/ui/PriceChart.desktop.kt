@file:Suppress("MagicNumber") // chart geometry/tuning constants read clearest as literals

package io.orbital.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

private const val GRID_LINE_COUNT = 3
private const val GRID_ALPHA = 0.15f
private const val FILL_ALPHA_TOP = 0.35f
private val STROKE_WIDTH = 2.dp
private val CROSSHAIR_DOT_RADIUS = 4.dp

private fun indexForOffset(x: Float, width: Float, count: Int): Int {
  if (count <= 1 || width <= 0f) return 0
  val stepX = width / (count - 1)
  return (x / stepX).roundToInt().coerceIn(0, count - 1)
}

private fun Modifier.trackHover(
    points: List<Double>,
    widthPx: Float,
    timestamps: List<Long>,
    interaction: ChartInteraction,
    onHoverIndexChange: (Int?) -> Unit
): Modifier =
    pointerInput(points, widthPx, timestamps, interaction.from, interaction.to) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent()
          val position = event.changes.firstOrNull()?.position
          if (event.type == PointerEventType.Scroll &&
              interaction.from != null &&
              interaction.to != null) {
            val span = interaction.to - interaction.from
            val factor = if ((event.changes.firstOrNull()?.scrollDelta?.y ?: 0f) > 0) 2.0 else 0.5
            val center = interaction.from + span / 2
            val half = (span * factor / 2).toLong().coerceAtLeast(30)
            interaction.onViewportChange?.invoke((center - half).coerceAtLeast(0), center + half)
          }
          onHoverIndexChange(
              if (event.type == PointerEventType.Exit || position == null) null
              else if (timestamps.size == points.size && timestamps.size > 1) {
                val span = (timestamps.last() - timestamps.first()).coerceAtLeast(1).toDouble()
                timestamps.indices.minByOrNull {
                  abs((timestamps[it] - timestamps.first()) / span * widthPx - position.x)
                }
              } else indexForOffset(position.x, widthPx, points.size))
        }
      }
    }

@Suppress(
    "LongParameterList",
    "CyclomaticComplexMethod") // Canvas geometry, gap segments, and hover rendering.
private fun DrawScope.drawPriceChart(
    points: List<Double>,
    lineColor: Color,
    gridColor: Color,
    interactive: Boolean,
    hoverIndex: Int?,
    timestamps: List<Long>,
    interaction: ChartInteraction
) {
  val minValue = points.min()
  val maxValue = points.max()
  val range = (maxValue - minValue).let { if (it > 0) it else 1.0 }
  val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f

  fun xFor(index: Int): Float =
      if (timestamps.size == points.size && timestamps.size > 1) {
        ((timestamps[index] - timestamps.first()).toDouble() /
                (timestamps.last() - timestamps.first()).coerceAtLeast(1) * size.width)
            .toFloat()
      } else index * stepX

  fun yFor(value: Double): Float =
      size.height - ((value - minValue) / range * size.height).toFloat()

  if (interactive) {
    repeat(GRID_LINE_COUNT) { i ->
      val y = size.height * (i + 1) / (GRID_LINE_COUNT + 1)
      drawLine(
          color = gridColor.copy(alpha = GRID_ALPHA),
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = 1f)
    }
  }

  val linePath =
      Path().apply {
        points.forEachIndexed { i, v ->
          val x = xFor(i)
          val y = yFor(v)
          val gap =
              i > 0 &&
                  timestamps.size == points.size &&
                  interaction.gaps.any { it.from > timestamps[i - 1] && it.to < timestamps[i] }
          if (i == 0 || gap) moveTo(x, y) else lineTo(x, y)
        }
      }
  val fillPath =
      Path().apply {
        addPath(linePath)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
      }

  if (interaction.gaps.isEmpty())
      drawPath(
          fillPath,
          brush =
              Brush.verticalGradient(
                  listOf(lineColor.copy(alpha = FILL_ALPHA_TOP), lineColor.copy(alpha = 0f))))
  drawPath(
      linePath,
      color = lineColor,
      style = Stroke(width = STROKE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

  hoverIndex?.let { idx ->
    val x = xFor(idx)
    val y = yFor(points[idx])
    drawLine(
        color = lineColor.copy(alpha = 0.4f),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1f)
    drawCircle(color = lineColor, radius = CROSSHAIR_DOT_RADIUS.toPx(), center = Offset(x, y))
  }
}

/**
 * Native Canvas-drawn sparkline/area chart — no embedded browser. Desktop previously rendered this
 * via a KCEF-embedded TradingView chart (matching the wasmJs implementation), but KCEF is an
 * archived, unmaintained dependency whose last-released Java bindings (2024.04.20.4) are
 * incompatible with the Chromium build it fetches at runtime (150.x) — `CefResourceReadCallback_N`
 * is missing, so the embedded HTML never loads. wasmJs is unaffected (it renders in a real browser)
 * and keeps the TradingView implementation.
 */
@Suppress("LongParameterList") // Platform chart contract includes timestamps.
@Composable
actual fun priceChart(
    modifier: Modifier,
    points: List<Double>,
    positive: Boolean,
    size: DpSize,
    interactive: Boolean,
    timestamps: List<Long>,
    interaction: ChartInteraction
) {
  val lineColor =
      if (positive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  var hoverIndex by remember(points) { mutableStateOf<Int?>(null) }
  val widthPx = with(LocalDensity.current) { size.width.toPx() }

  Box(modifier = modifier.size(size)) {
    Canvas(
        modifier =
            Modifier.fillMaxSize().let { base ->
              if (interactive)
                  base.trackHover(points, widthPx, timestamps, interaction) { hoverIndex = it }
              else base
            }) {
          drawPriceChart(
              points, lineColor, gridColor, interactive, hoverIndex, timestamps, interaction)
        }

    hoverIndex?.let { idx ->
      Text(
          formatUsd(points[idx]) +
              timestamps.getOrNull(idx)?.let { " · ${Instant.ofEpochSecond(it)}" }.orEmpty(),
          modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
          color = lineColor,
          fontSize = 11.sp)
    }
  }
}
