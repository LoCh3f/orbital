package com.orbital.models

import kotlinx.serialization.Serializable

/** Observation time in Unix seconds and price in USD. */
@Serializable data class PriceHistoryPoint(val timestamp: Long, val priceUsd: Double)
