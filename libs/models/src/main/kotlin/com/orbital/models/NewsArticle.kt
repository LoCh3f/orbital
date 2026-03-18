package com.orbital.models

// import kotlinx.serialization.Serializable

// @Serializable
data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String,
    val source: String,
    val publishedAt: String,
    val url: String
)
