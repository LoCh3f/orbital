@file:Suppress("MagicNumber") // vector path coordinates read clearest as literals, not constants

package io.orbital.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private const val ICON_SIZE = 24f
private const val ICON_STROKE_WIDTH = 2f

/** A simple upward-trending zigzag — used as the Market section's nav icon. */
@Composable
fun marketIcon(): ImageVector = remember {
  ImageVector.Builder(
          name = "MarketIcon",
          defaultWidth = ICON_SIZE.dp,
          defaultHeight = ICON_SIZE.dp,
          viewportWidth = ICON_SIZE,
          viewportHeight = ICON_SIZE)
      .apply {
        group {
          path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeLineWidth = ICON_STROKE_WIDTH,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
              pathFillType = PathFillType.NonZero) {
                moveTo(3f, 17f)
                lineTo(9f, 11f)
                lineTo(13f, 15f)
                lineTo(21f, 6f)
              }
          path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeLineWidth = ICON_STROKE_WIDTH,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
              pathFillType = PathFillType.NonZero) {
                moveTo(15f, 6f)
                lineTo(21f, 6f)
                lineTo(21f, 12f)
              }
        }
      }
      .build()
}

/** A simple lined-document glyph — used as the News section's nav icon. */
@Composable
fun newsIcon(): ImageVector = remember {
  ImageVector.Builder(
          name = "NewsIcon",
          defaultWidth = ICON_SIZE.dp,
          defaultHeight = ICON_SIZE.dp,
          viewportWidth = ICON_SIZE,
          viewportHeight = ICON_SIZE)
      .apply {
        group {
          path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeLineWidth = ICON_STROKE_WIDTH,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
              pathFillType = PathFillType.NonZero) {
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                close()
              }
          path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeLineWidth = ICON_STROKE_WIDTH,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
              pathFillType = PathFillType.NonZero) {
                moveTo(7f, 8f)
                lineTo(17f, 8f)
              }
          path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeLineWidth = ICON_STROKE_WIDTH,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
              pathFillType = PathFillType.NonZero) {
                moveTo(7f, 12f)
                lineTo(17f, 12f)
              }
          path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeLineWidth = ICON_STROKE_WIDTH,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
              pathFillType = PathFillType.NonZero) {
                moveTo(7f, 16f)
                lineTo(13f, 16f)
              }
        }
      }
      .build()
}
