package io.orbital.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import com.orbital.orbital_app.generated.resources.Res
import io.orbital.app.data.HistoryGap

/**
 * A price trend chart rendered via TradingView's Lightweight Charts — a compact, static sparkline
 * by default, or a bigger chart with visible scales, grid, and pan/zoom/crosshair when
 * [interactive] is true. [size] is required (rather than inferred from layout) so platform
 * implementations can compute pixel dimensions once at composition time instead of reacting to a
 * later measurement pass — the async measure-then-recompose route left stray chart instances behind
 * on wasmJs.
 */
@Suppress("LongParameterList") // Platform chart contract includes timestamps.
@Composable
expect fun priceChart(
    modifier: Modifier,
    points: List<Double>,
    positive: Boolean,
    size: DpSize,
    interactive: Boolean = false,
    timestamps: List<Long> = emptyList(),
    interaction: ChartInteraction = ChartInteraction()
)

data class ChartInteraction(
    val gaps: List<HistoryGap> = emptyList(),
    val from: Long? = null,
    val to: Long? = null,
    val onViewportChange: ((Long, Long) -> Unit)? = null
)

internal suspend fun loadLightweightChartsLibrarySource(): String =
    Res.readBytes("files/lightweight-charts.standalone.production.js").decodeToString()

// Timestamped observations use Unix seconds; fallback sparklines have a relative axis.
internal const val RENDER_SPARKLINE_JS =
    """
window.renderSparkline = function(container, valuesCsv, positive, width, height, interactive, timesCsv, gapsCsv, viewFrom, viewTo, onViewport) {
  if (!container || !valuesCsv || !width || !height) return;
  var values = valuesCsv.split(',').map(Number);
  if (values.length < 2) return;
  container.__onViewport = onViewport;
  var signature = [valuesCsv, timesCsv, gapsCsv, width, height, positive, viewFrom, viewTo].join('|');
  if (container.__signature === signature) return;
  container.__signature = signature;
  container.__rendering = true;
  var color = positive ? '#10B981' : '#F43F5E';
  var chart = container.__chart;
  if (!chart) {
    chart = LightweightCharts.createChart(container, {
      width: width, height: height,
      layout: { background: { color: 'transparent' }, textColor: interactive ? '#B9CACB' : 'transparent', attributionLogo: false },
      grid: { vertLines: { visible: interactive, color: 'rgba(185,202,203,0.08)' }, horzLines: { visible: interactive, color: 'rgba(185,202,203,0.08)' } },
      rightPriceScale: { visible: interactive, borderVisible: false },
      timeScale: { visible: interactive && !!timesCsv, borderVisible: false, timeVisible: true },
      handleScroll: interactive, handleScale: interactive,
      crosshair: { horzLine: { visible: interactive, labelVisible: interactive }, vertLine: { visible: interactive, labelVisible: interactive } }
    });
    container.__chart = chart;
    container.__series = [];
    chart.timeScale().subscribeVisibleTimeRangeChange(function(range) {
      if (!range || container.__rendering || !timesCsv || !viewFrom) return;
      clearTimeout(container.__viewportTimer);
      container.__viewportTimer = setTimeout(function() {
        if (container.isConnected && container.__onViewport) container.__onViewport(Number(range.from), Number(range.to));
      }, 400);
    });
  }
  chart.resize(width, height);
  container.__series.forEach(function(series) { chart.removeSeries(series); });
  container.__series = [];
  var times = timesCsv ? timesCsv.split(',').map(Number) : [];
  var gaps = gapsCsv ? gapsCsv.split(',').map(function(gap) { return gap.split(':').map(Number); }) : [];
  var segments = [[]];
  values.forEach(function(value, index) {
    var time = times.length === values.length ? times[index] : index + 1;
    if (!Number.isFinite(value)) return;
    if (index && gaps.some(function(gap) { return gap[0] > times[index - 1] && gap[1] < time; })) segments.push([]);
    segments[segments.length - 1].push({ time: time, value: value });
  });
  segments.forEach(function(points, index) {
    if (!points.length) return;
    var series = chart.addSeries(LightweightCharts.AreaSeries, {
      lineColor: color, topColor: color + '40', bottomColor: color + '00', lineWidth: 2,
      priceLineVisible: interactive && index === segments.length - 1,
      lastValueVisible: interactive && index === segments.length - 1, crosshairMarkerVisible: interactive
    });
    series.setData(points);
    container.__series.push(series);
  });
  if (viewFrom > 0 && viewTo > viewFrom) chart.timeScale().setVisibleRange({ from: viewFrom, to: viewTo });
  else chart.timeScale().fitContent();
  requestAnimationFrame(function() { container.__rendering = false; });
};
"""
