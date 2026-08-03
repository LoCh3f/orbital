package com.orbital.models

import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    val id: String,
    val symbol: String,
    val name: String,
    val currentPrice: Double,
    val priceChange24h: Double?,
    val marketCap: Long
)
