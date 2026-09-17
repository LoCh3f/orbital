package io.orbital.app.data

import kotlinx.serialization.Serializable

// Mirrors the wire shape of com.orbital.models.NewsArticle (see libs/models). Kept as a
// separate, minimal client-side DTO rather than a shared multiplatform dependency because
// NewsArticle uses java.time.Instant and java.util.UUID, neither available on the wasmJs
// target (see MarketPrice.kt for the same reasoning on the market side).
@Serializable
data class NewsItem(
    val id: String,
    val title: String,
    val description: String,
    val url: String,
    val sourceName: String,
    val publishedAt: String,
    val category: String
)
