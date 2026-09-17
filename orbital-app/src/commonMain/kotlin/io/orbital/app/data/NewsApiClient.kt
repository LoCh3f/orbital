package io.orbital.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

class NewsApiClient(
    private val baseUrl: String = defaultGatewayUrl(),
    private val client: HttpClient = createHttpClient()
) {
  suspend fun fetchNews(): List<NewsItem> =
      client.get("$baseUrl/api/v1/news").body<NewsResponse>().data
}

@Serializable private data class NewsResponse(val data: List<NewsItem>)
