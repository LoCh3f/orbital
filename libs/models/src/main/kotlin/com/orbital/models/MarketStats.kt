package com.orbital.models

import kotlinx.serialization.Serializable

@Serializable
data class MarketStats(
    val coinId: String,
    val ath: Double,
    val atl: Double,
    val circulatingSupply: Double,
    val totalSupply: Double,
    val sparkline7d: List<Double>
)
