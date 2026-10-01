package io.orbital.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orbital.app.data.NewsItem

private const val DESCRIPTION_MAX_LINES = 3

val NEWS_CATEGORIES = listOf("CRYPTO", "MARKETS", "MACRO", "TECH")

private const val CHIP_CORNER_PERCENT = 50

private val CARD_SHAPE = RoundedCornerShape(8.dp)
private val CHIP_SHAPE = RoundedCornerShape(CHIP_CORNER_PERCENT)
private val FIELD_SHAPE = RoundedCornerShape(12.dp)
private val ACCENT_BAR_WIDTH = 3.dp

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
    TextField(
        value = filters.query,
        onValueChange = filters.onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = { Text("Search news") },
        singleLine = true,
        shape = FIELD_SHAPE,
        colors = newsSearchFieldColors())

    when (state) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
      }
      is UiState.Error -> {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                  "Unable to load news: ${state.message}",
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
              Button(onClick = onRefresh) { Text("Retry") }
            }
      }
      is UiState.Success -> {
        val filtered = filterArticles(state.data, filters.query)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(filtered) { article -> newsArticleRow(article) }
            }
      }
    }
  }
}

@Composable
private fun newsSearchFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant)

private fun filterArticles(articles: List<NewsItem>, query: String): List<NewsItem> {
  if (query.isBlank()) return articles
  val needle = query.trim().lowercase()
  return articles.filter {
    it.title.lowercase().contains(needle) || it.description.lowercase().contains(needle)
  }
}

@Composable
private fun categoryChips(selectedCategory: String?, onCategorySelected: (String?) -> Unit) {
  LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
    items(listOf(null) + NEWS_CATEGORIES) { category ->
      FilterChip(
          modifier = Modifier.padding(end = 8.dp),
          selected = category == selectedCategory,
          onClick = { onCategorySelected(category) },
          label = { Text(category ?: "All") },
          shape = CHIP_SHAPE,
          colors =
              FilterChipDefaults.filterChipColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                  labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
          border = null)
    }
  }
}

@Composable
private fun newsArticleRow(article: NewsItem) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = CARD_SHAPE,
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
          Box(
              modifier =
                  Modifier.fillMaxHeight()
                      .width(ACCENT_BAR_WIDTH)
                      .background(MaterialTheme.colorScheme.primaryContainer))
          Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(
                      article.sourceName,
                      color = MaterialTheme.colorScheme.primary,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 12.sp)
                  categoryBadge(article.category)
                }
            Text(
                article.title,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp)
            Text(
                article.publishedAt,
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp)
            Text(
                article.description,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis)
          }
        }
      }
}

@Composable
private fun categoryBadge(category: String) {
  Box(
      modifier =
          Modifier.background(
                  MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(
            category,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold)
      }
}
