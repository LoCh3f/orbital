@file:Suppress("MagicNumber") // color hex literals and vector coordinates read clearest as-is

package io.orbital.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Ported from the "Orbital" design system generated in Stitch (Cosmic Tech Glassmorphism):
// a dark-only telemetry theme, so this is the app's single color scheme regardless of the
// system theme.
val ORBITAL_DARK_COLOR_SCHEME: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFE0FDFF),
        onPrimary = Color(0xFF00373A),
        primaryContainer = Color(0xFF00F2FE),
        onPrimaryContainer = Color(0xFF006A70),
        inversePrimary = Color(0xFF00696F),
        secondary = Color(0xFF4EDEA3),
        onSecondary = Color(0xFF003824),
        secondaryContainer = Color(0xFF00A572),
        onSecondaryContainer = Color(0xFF00311F),
        tertiary = Color(0xFFFFF5F4),
        onTertiary = Color(0xFF67001B),
        tertiaryContainer = Color(0xFFFFCFD1),
        onTertiaryContainer = Color(0xFFBE0D3C),
        background = Color(0xFF10131A),
        onBackground = Color(0xFFE1E2EB),
        surface = Color(0xFF10131A),
        onSurface = Color(0xFFE1E2EB),
        surfaceVariant = Color(0xFF32353C),
        onSurfaceVariant = Color(0xFFB9CACB),
        surfaceTint = Color(0xFF00DCE6),
        inverseSurface = Color(0xFFE1E2EB),
        inverseOnSurface = Color(0xFF2E3037),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF849495),
        outlineVariant = Color(0xFF3A494B),
        surfaceBright = Color(0xFF363940),
        surfaceDim = Color(0xFF10131A),
        surfaceContainer = Color(0xFF1D2026),
        surfaceContainerHigh = Color(0xFF272A31),
        surfaceContainerHighest = Color(0xFF32353C),
        surfaceContainerLow = Color(0xFF191C22),
        surfaceContainerLowest = Color(0xFF0B0E14))

private val LOGO_GRADIENT_START = Color(0xFF00F2FE)
private val LOGO_GRADIENT_END = Color(0xFF4FACFE)
private val LOGO_OUTER_RING = Color(0xFF1E293B)
private val LOGO_CORE_BACKGROUND = Color(0xFF0B0E14)
private val LOGO_ACCENT_SECONDARY = Color(0xFF10B981)

/** Renders the Orbital brand mark: two crossed orbit rings around a glowing core. */
@Composable
fun orbitalLogoMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
  Canvas(modifier = modifier.size(size)) {
    val d = this.size.minDimension
    val gradient =
        Brush.linearGradient(
            colors = listOf(LOGO_GRADIENT_START, LOGO_GRADIENT_END),
            start = Offset.Zero,
            end = Offset(this.size.width, this.size.height))

    drawCircle(color = LOGO_OUTER_RING, radius = d * 0.46f, style = Stroke(width = d * 0.02f))

    val orbitSize = Size(d * 0.76f, d * 0.28f)
    val orbitTopLeft = Offset(center.x - orbitSize.width / 2f, center.y - orbitSize.height / 2f)
    rotate(-30f) {
      drawOval(
          brush = gradient,
          topLeft = orbitTopLeft,
          size = orbitSize,
          style = Stroke(width = d * 0.03f))
    }
    rotate(35f) {
      drawOval(
          brush = gradient,
          topLeft = orbitTopLeft,
          size = orbitSize,
          alpha = 0.6f,
          style = Stroke(width = d * 0.02f))
    }

    drawCircle(color = LOGO_CORE_BACKGROUND, radius = d * 0.16f)
    drawCircle(color = LOGO_GRADIENT_START, radius = d * 0.16f, style = Stroke(width = d * 0.02f))
    drawCircle(brush = gradient, radius = d * 0.08f)
    drawCircle(
        color = LOGO_GRADIENT_START,
        radius = d * 0.045f,
        center = center + Offset(d * 0.28f, -d * 0.16f))
    drawCircle(
        color = LOGO_ACCENT_SECONDARY,
        radius = d * 0.03f,
        center = center + Offset(-d * 0.28f, d * 0.16f))
  }
}
