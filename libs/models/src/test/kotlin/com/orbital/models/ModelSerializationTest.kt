package com.orbital.models

import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModelSerializationTest {
  private val json = Json { prettyPrint = true }

  @Test
  fun `coin price round trips`() {
    val value =
        CoinPrice(
            coinId = "bitcoin",
            symbol = "BTC",
            name = "Bitcoin",
            currentPriceUsd = 65000.0,
            marketCapUsd = 1_280_000_000_000.0,
            volume24hUsd = 24_000_000_000.0,
            priceChangePercent24h = 2.35,
            lastUpdated = Instant.parse("2024-01-01T00:00:00Z"))
    val encoded = json.encodeToString(value)
    val decoded = json.decodeFromString<CoinPrice>(encoded)
    assertEquals(value, decoded)
  }

  @Test
  fun `news article round trips`() {
    val value =
        NewsArticle(
            id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            title = "Orbital launch",
            description = "A new release is here",
            url = "https://example.com/news",
            sourceName = "Orbital",
            publishedAt = Instant.parse("2024-01-01T00:00:00Z"),
            category = NewsCategory.CRYPTO)
    val encoded = json.encodeToString(value)
    val decoded = json.decodeFromString<NewsArticle>(encoded)
    assertEquals(value, decoded)
  }
}
