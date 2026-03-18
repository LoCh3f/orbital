package com.orbital.market.api

import kotlinx.serialization.Serializable

@Serializable
data class CoinGeckoROI(
    val times: Double? = null,
    val currency: String? = null,
    val percentage: Double? = null
)
