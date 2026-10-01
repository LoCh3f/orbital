package io.orbital.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders a coin's logo from [url], filling [modifier]'s bounds, and reports whether it actually
 * loaded via [onResult]. Many logos have transparent backgrounds (e.g. a symbol-only mark with no
 * fill), so a caller-provided fallback (e.g. a text avatar) must be hidden — not just drawn
 * underneath — once [onResult] reports success, or it bleeds through the transparent pixels.
 *
 * Platform implementations differ because CoinGecko's asset CDN sends no CORS headers: fetching the
 * bytes ourselves (as desktop does) is blocked by the browser on wasmJs, which instead uses a plain
 * `<img>` element — browsers don't require CORS just to display an image, only to read its pixels
 * back via `fetch`/canvas.
 */
@Composable expect fun coinLogoImage(modifier: Modifier, url: String, onResult: (Boolean) -> Unit)
