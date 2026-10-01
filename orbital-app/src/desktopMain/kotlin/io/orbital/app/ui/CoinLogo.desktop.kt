package io.orbital.app.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.orbital.app.data.createHttpClient

private val client by lazy { createHttpClient() }
private val logoCache = mutableMapOf<String, ImageBitmap>()

private suspend fun fetchCoinLogo(url: String): ImageBitmap? =
    runCatching { client.get(url).body<ByteArray>().decodeToImageBitmap() }.getOrNull()

@Composable
actual fun coinLogoImage(modifier: Modifier, url: String, onResult: (Boolean) -> Unit) {
  var bitmap by remember(url) { mutableStateOf(logoCache[url]) }

  LaunchedEffect(url) {
    val cached = logoCache[url]
    if (cached != null) {
      bitmap = cached
      onResult(true)
      return@LaunchedEffect
    }
    val loaded = fetchCoinLogo(url)
    if (loaded != null) {
      logoCache[url] = loaded
      bitmap = loaded
    }
    onResult(loaded != null)
  }

  bitmap?.let {
    Image(
        bitmap = it,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop)
  }
}
