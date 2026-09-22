package io.orbital.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.orbital.app.data.NewsItem

private const val DESCRIPTION_MAX_LINES = 3
private const val CHIP_SELECTED_ARGB = 0xFF1565C0L
private const val CHIP_UNSELECTED_ARGB = 0xFFE0E0E0L
private val CHIP_SELECTED_COLOR = Color(CHIP_SELECTED_ARGB)
private val CHIP_UNSELECTED_COLOR = Color(CHIP_UNSELECTED_ARGB)

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
      categoryChip(category, selectedCategory, onCategorySelected)
    }
  }
}

@Composable
private fun categoryChip(
    category: String?,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
  val selected = category == selectedCategory
  Box(
      modifier =
          Modifier.padding(end = 8.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(if (selected) CHIP_SELECTED_COLOR else CHIP_UNSELECTED_COLOR)
              .clickable { onCategorySelected(category) }
              .padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            category ?: "All",
            color = if (selected) Color.White else MaterialTheme.colors.onSurface)
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
