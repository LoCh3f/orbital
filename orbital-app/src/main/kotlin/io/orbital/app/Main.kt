package io.orbital.app

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

@Composable
@Preview
fun orbitalApp() {
  val client = remember { HttpClient(CIO) }
  var marketText by remember { mutableStateOf("Loading market...") }
  var newsText by remember { mutableStateOf("Loading news...") }

  LaunchedEffect(Unit) {
    runCatching {
          val m = client.get("http://127.0.0.1:8080/api/v1/market/prices")
          m.bodyAsText()
        }
        .onSuccess { marketText = it }
        .onFailure { marketText = "Unable to reach gateway: ${it.message}" }

    runCatching {
          val n = client.get("http://127.0.0.1:8080/api/v1/news")
          n.bodyAsText()
        }
        .onSuccess { newsText = it }
        .onFailure { newsText = "Unable to reach gateway: ${it.message}" }
  }

  MaterialTheme {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
      Text("Orbital")
      Text("Market:")
      Text(marketText)
      Text("\nNews:")
      Text(newsText)
    }
  }
}

fun main() = application {
  Window(onCloseRequest = ::exitApplication, title = "Orbital") { orbitalApp() }
}
