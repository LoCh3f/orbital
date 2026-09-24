package com.orbital.models

import kotlinx.serialization.Serializable

/**
 * Extended stats for a single coin, as returned by `GET /api/v1/market/stats/{coinId}`.
 * [sparkline7d] is currently always empty — CoinGecko's sparkline data isn't mapped yet.
 */
@Serializable
data class MarketStats(
    val coinId: String,
    val ath: Double,
    val atl: Double,
    val circulatingSupply: Double,
    val totalSupply: Double,
    val sparkline7d: List<Double>
)
