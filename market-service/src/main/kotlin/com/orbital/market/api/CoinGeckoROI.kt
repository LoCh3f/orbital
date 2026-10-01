package com.orbital.market.api

import kotlinx.serialization.Serializable

/** Optional return-on-investment block CoinGecko includes for some coins (e.g. staking tokens). */
@Serializable
data class CoinGeckoROI(
    val times: Double? = null,
    val currency: String? = null,
    val percentage: Double? = null
)
