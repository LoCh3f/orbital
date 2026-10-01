package com.orbital.news.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val DEFAULT_PAGE_SIZE = 10

/**
 * Wraps NewsAPI's `/v2/top-headlines` endpoint. **Never actually sends an API key**, so real
 * requests always 401 and every call falls back to [fallbackArticles] — this is a deliberate,
 * temporary state (real NewsAPI integration costs money and is intentionally deferred), not a bug.
 */
class NewsApiClient(private val client: HttpClient = defaultClient()) {
  /** Always returns [fallbackArticles] today; see the class doc for why. */
  suspend fun fetchTopHeadlines(category: String? = null): List<NewsApiArticle> {
    return runCatching {
          val response =
              client.get("https://newsapi.org/v2/top-headlines") {
                parameter("country", "us")
                if (!category.isNullOrBlank()) parameter("category", category)
                parameter("pageSize", DEFAULT_PAGE_SIZE)
              }

          val body = response.body<String>()
          val parsed = Json.decodeFromString<NewsApiResponse>(body)
          if (parsed.status != "ok" || parsed.articles.isEmpty()) {
            return@runCatching fallbackArticles()
          }

          parsed.articles
        }
        .getOrElse { fallbackArticles() }
  }

  private fun fallbackArticles(): List<NewsApiArticle> =
      listOf(
          NewsApiArticle(
              title = "Crypto markets showing resilience",
              description =
                  "Major digital assets remain active as traders watch key support levels.",
              url = "https://example.com/crypto-markets-resilience",
              source = NewsApiSource(name = "Orbital Mock"),
              publishedAt = "2024-01-01T12:00:00Z"),
          NewsApiArticle(
              title = "DeFi protocols expand cross-chain integrations",
              description = "Several projects announce new bridges and liquidity incentives.",
              url = "https://example.com/defi-cross-chain",
              source = NewsApiSource(name = "Orbital Mock"),
              publishedAt = "2024-01-01T13:00:00Z"))

  companion object {
    fun defaultClient(): HttpClient =
        HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
  }
}

/** Raw shape of a NewsAPI `/v2/top-headlines` response. */
@kotlinx.serialization.Serializable
data class NewsApiResponse(
    val status: String? = null,
    val articles: List<NewsApiArticle> = emptyList(),
    val code: String? = null,
    val message: String? = null
)

@kotlinx.serialization.Serializable
data class NewsApiArticle(
    val title: String,
    val description: String? = null,
    val url: String,
    val source: NewsApiSource? = null,
    val publishedAt: String? = null
)

@kotlinx.serialization.Serializable data class NewsApiSource(val name: String)
