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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.orbital.app.data.NewsItem

private const val DESCRIPTION_MAX_LINES = 3

val NEWS_CATEGORIES = listOf("CRYPTO", "MARKETS", "MACRO", "TECH")

/** Bundles the news screen's filter state to keep [newsScreen]'s parameter list small. */
data class NewsFilters(
    val category: String?,
    val onCategoryChange: (String?) -> Unit,
    val query: String,
    val onQueryChange: (String) -> Unit
)

@Composable
fun newsScreen(state: UiState<List<NewsItem>>, filters: NewsFilters, onRefresh: () -> Unit) {
  Column(modifier = Modifier.fillMaxSize()) {
    categoryChips(filters.category, filters.onCategoryChange)
    OutlinedTextField(
        value = filters.query,
        onValueChange = filters.onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        label = { Text("Search news") },
        singleLine = true)

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
        val filtered = filterArticles(state.data, filters.query)
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
              items(filtered) { article -> newsArticleRow(article) }
            }
      }
    }
  }
}

private fun filterArticles(articles: List<NewsItem>, query: String): List<NewsItem> {
  if (query.isBlank()) return articles
  val needle = query.trim().lowercase()
  return articles.filter {
    it.title.lowercase().contains(needle) || it.description.lowercase().contains(needle)
  }
}

@Composable
private fun categoryChips(selectedCategory: String?, onCategorySelected: (String?) -> Unit) {
  LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
    items(listOf(null) + NEWS_CATEGORIES) { category ->
      FilterChip(
          modifier = Modifier.padding(end = 8.dp),
          selected = category == selectedCategory,
          onClick = { onCategorySelected(category) },
          label = { Text(category ?: "All") })
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
