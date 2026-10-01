package io.orbital.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import com.orbital.orbital_app.generated.resources.Res

/**
 * A price trend chart rendered via TradingView's Lightweight Charts — a compact, static sparkline
 * by default, or a bigger chart with visible scales, grid, and pan/zoom/crosshair when
 * [interactive] is true. [size] is required (rather than inferred from layout) so platform
 * implementations can compute pixel dimensions once at composition time instead of reacting to a
 * later measurement pass — the async measure-then-recompose route left stray chart instances behind
 * on wasmJs.
 */
@Composable
expect fun priceChart(
    modifier: Modifier,
    points: List<Double>,
    positive: Boolean,
    size: DpSize,
    interactive: Boolean = false
)

internal suspend fun loadLightweightChartsLibrarySource(): String =
    Res.readBytes("files/lightweight-charts.standalone.production.js").decodeToString()

// Styling mirrors the semantic colors in OrbitalTheme.kt: emerald for positive, crimson for
// negative. Time is synthesized as hourly points ending "now" since the backend only sends raw
// price values (CoinGecko's sparkline_in_7d.price), not real timestamps.
internal const val RENDER_SPARKLINE_JS =
    """
window.renderSparkline = function(container, valuesCsv, positive, width, height, interactive) {
  if (!container || !valuesCsv || !width || !height) return;
  var values = valuesCsv.split(',').map(Number).filter(function(v) { return !isNaN(v); });
  if (values.length < 2) return;
  container.innerHTML = '';
  var color = positive ? '#10B981' : '#F43F5E';
  var mutedText = '#B9CACB';
  var gridColor = 'rgba(185, 202, 203, 0.08)';
  var chart = LightweightCharts.createChart(container, {
    width: width,
    height: height,
    layout: {
      background: { color: 'transparent' },
      textColor: interactive ? mutedText : 'transparent',
      attributionLogo: false
    },
    grid: {
      vertLines: { visible: interactive, color: gridColor },
      horzLines: { visible: interactive, color: gridColor }
    },
    rightPriceScale: { visible: interactive, borderVisible: false },
    timeScale: { visible: interactive, borderVisible: false, timeVisible: true },
    handleScroll: interactive,
    handleScale: interactive,
    crosshair: {
      horzLine: { visible: interactive, labelVisible: interactive },
      vertLine: { visible: interactive, labelVisible: interactive }
    }
  });
  var series = chart.addSeries(LightweightCharts.AreaSeries, {
    lineColor: color,
    topColor: color + '40',
    bottomColor: color + '00',
    lineWidth: 2,
    priceLineVisible: interactive,
    lastValueVisible: interactive,
    crosshairMarkerVisible: interactive
  });
  var now = Math.floor(Date.now() / 1000);
  var points = values.map(function(v, i) {
    return { time: now - (values.length - 1 - i) * 3600, value: v };
  });
  series.setData(points);
  chart.timeScale().fitContent();
};
"""

// The WebView's body fills the whole embedded chart area on desktop, so it's used directly as
// the chart container rather than a nested div (sidesteps relying on percentage-sized nested
// elements resolving correctly at load time).
internal fun sparklineHtmlDocument(
    libraryJs: String,
    valuesCsv: String,
    positive: Boolean,
    interactive: Boolean
): String =
    """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>html, body { margin: 0; padding: 0; background: transparent; overflow: hidden; width: 100%; height: 100%; }</style>
<script>$libraryJs</script>
<script>$RENDER_SPARKLINE_JS</script>
</head>
<body>
<script>
  window.addEventListener('load', function() {
    renderSparkline(document.body, '$valuesCsv', $positive, window.innerWidth, window.innerHeight, $interactive);
  });
</script>
</body>
</html>
"""
