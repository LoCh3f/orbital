package com.orbital.news.api

import com.orbital.core.DateTimeUtils
import com.orbital.models.NewsArticle
import com.orbital.models.NewsCategory
import java.util.UUID

/** Translates NewsAPI's wire format ([NewsApiArticle]) into the domain [NewsArticle] type. */
object NewsMapper {
  /**
   * Maps [article] to a [NewsArticle] tagged with the given [category] (the caller resolves this
   * from the request, since NewsAPI's own article payload has no category field). Missing/blank
   * fields fall back to sensible defaults (`"Untitled"`, `"Unknown"`, a fixed placeholder date)
   * rather than failing.
   */
  fun toDomain(article: NewsApiArticle, category: NewsCategory): NewsArticle =
      NewsArticle(
          id = UUID.randomUUID(),
          title = article.title.ifBlank { "Untitled" },
          description = article.description.orEmpty(),
          url = article.url,
          sourceName = article.source?.name ?: "Unknown",
          publishedAt = DateTimeUtils.parse(article.publishedAt ?: "2024-01-01T00:00:00Z"),
          category = category)
}
