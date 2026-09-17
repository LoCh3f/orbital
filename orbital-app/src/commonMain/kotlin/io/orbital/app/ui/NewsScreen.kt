package io.orbital.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.orbital.app.data.NewsItem

private const val DESCRIPTION_MAX_LINES = 3

@Composable
fun newsScreen(state: UiState<List<NewsItem>>, onRefresh: () -> Unit) {
  when (state) {
    is UiState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
    is UiState.Error -> {
      Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Unable to load news: ${state.message}")
            Button(onClick = onRefresh) { Text("Retry") }
          }
    }
    is UiState.Success -> {
      LazyColumn(
          modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(state.data) { article -> newsArticleRow(article) }
          }
    }
  }
}

@Composable
private fun newsArticleRow(article: NewsItem) {
  Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
      Text(article.title)
      Row(
          modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${article.sourceName} · ${article.publishedAt}")
            Text(article.category)
          }
      Text(
          article.description,
          modifier = Modifier.padding(top = 4.dp),
          maxLines = DESCRIPTION_MAX_LINES,
          overflow = TextOverflow.Ellipsis)
    }
  }
}
