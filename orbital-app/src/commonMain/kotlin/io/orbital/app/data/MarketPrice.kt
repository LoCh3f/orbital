package io.orbital.app.data

import kotlinx.serialization.Serializable

// Mirrors the wire shape of com.orbital.models.CoinPrice (see libs/models). Kept as a
// separate, minimal client-side DTO rather than a shared multiplatform dependency because
// CoinPrice uses java.time.Instant, which isn't available on the wasmJs target.
@Serializable
data class MarketPrice(
    val coinId: String,
    val symbol: String,
    val name: String,
    val currentPriceUsd: Double,
    val marketCapUsd: Double,
    val volume24hUsd: Double,
    val priceChangePercent24h: Double,
    val lastUpdated: String
)
