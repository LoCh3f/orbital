package com.orbital.market.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw shape of CoinGecko's `/coins/{id}/market_chart` response — parallel series of `[timestampMs,
 * value]` pairs, one entry per sampled point. [prices], [marketCaps], and [totalVolumes] share the
 * same length and timestamps; mapped via [CoinGeckoMapper.toHistorySnapshots].
 */
@Serializable
data class CoinGeckoMarketChart(
    val prices: List<List<Double>> = emptyList(),
    @SerialName("market_caps") val marketCaps: List<List<Double>> = emptyList(),
    @SerialName("total_volumes") val totalVolumes: List<List<Double>> = emptyList()
)
