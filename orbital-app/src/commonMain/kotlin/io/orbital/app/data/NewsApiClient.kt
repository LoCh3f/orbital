package io.orbital.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

class NewsApiClient(
    private val baseUrl: String = defaultGatewayUrl(),
    private val client: HttpClient = createHttpClient()
) {
  suspend fun fetchNews(category: String? = null): List<NewsItem> =
      client
          .get("$baseUrl/api/v1/news") {
            if (!category.isNullOrBlank()) parameter("category", category)
          }
          .body<NewsResponse>()
          .data
}

@Serializable private data class NewsResponse(val data: List<NewsItem>)
