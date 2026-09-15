package com.orbital.news.api

import com.orbital.core.DateTimeUtils
import com.orbital.models.NewsArticle
import com.orbital.models.NewsCategory
import java.util.UUID

object NewsMapper {
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
