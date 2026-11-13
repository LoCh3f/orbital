package com.orbital.models

import kotlinx.serialization.Serializable

@Serializable data class HistoryValue(val timestamp: Long, val price: Double)

@Serializable data class HistoryGap(val from: Long, val to: Long)

/** One source and quote per series; exchange quotes are never relabelled as USD. */
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
