package io.orbital.app.data

import kotlinx.serialization.Serializable

@Serializable data class HistoryValue(val timestamp: Long, val price: Double)

@Serializable data class HistoryGap(val from: Long, val to: Long)

@Serializable
data class HistorySeries(
    val coinId: String,
    val source: String,
    val quote: String,
    val pair: String? = null,
    val points: List<HistoryValue>,
    val from: Long,
    val to: Long,
    val resolutionSeconds: Long,
    val earliestAvailable: Long? = null,
    val latestAvailable: Long? = null,
    val gaps: List<HistoryGap> = emptyList(),
    val importing: Boolean = false,
    val notice: String? = null,
    val binanceAvailable: Boolean = false
)

data class HistoryRequest(
    val coinId: String,
    val source: String = "auto",
    val range: String = "all",
    val from: Long? = null,
    val to: Long? = null
)
