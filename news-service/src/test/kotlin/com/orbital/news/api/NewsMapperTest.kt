package com.orbital.news.api

import com.orbital.models.NewsCategory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class NewsMapperTest {
  @Test
  fun `toDomain honors the requested category`() {
    val article =
        NewsApiArticle(
            title = "Orbital launch",
            description = "A new release is here",
            url = "https://example.com/news",
            source = NewsApiSource(name = "Orbital"),
            publishedAt = "2024-01-01T00:00:00Z")

    val domain = NewsMapper.toDomain(article, NewsCategory.MARKETS)

    assertEquals(NewsCategory.MARKETS, domain.category)
    assertEquals("Orbital launch", domain.title)
    assertEquals("A new release is here", domain.description)
    assertEquals("Orbital", domain.sourceName)
    assertEquals(Instant.parse("2024-01-01T00:00:00Z"), domain.publishedAt)
  }

  @Test
  fun `toDomain falls back for blank or missing fields`() {
    val article =
        NewsApiArticle(
            title = "",
            description = null,
            url = "https://example.com/news",
            source = null,
            publishedAt = null)

    val domain = NewsMapper.toDomain(article, NewsCategory.TECH)

    assertEquals("Untitled", domain.title)
    assertEquals("", domain.description)
    assertEquals("Unknown", domain.sourceName)
    assertEquals(Instant.parse("2024-01-01T00:00:00Z"), domain.publishedAt)
    assertEquals(NewsCategory.TECH, domain.category)
  }
}
