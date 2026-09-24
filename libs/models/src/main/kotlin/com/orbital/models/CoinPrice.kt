package com.orbital.models

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A single coin's current price snapshot, as returned by `GET /api/v1/market/prices`. */
@Serializable
data class CoinPrice(
    val coinId: String,
    val symbol: String,
    val name: String,
    val currentPriceUsd: Double,
    val marketCapUsd: Double,
    val volume24hUsd: Double,
    val priceChangePercent24h: Double,
    @Serializable(with = InstantSerializer::class) val lastUpdated: Instant
)

/** Serializes [Instant] as a plain ISO-8601 string rather than kotlinx.serialization's default. */
object InstantSerializer : KSerializer<Instant> {
  override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeString(value.toString())
  }

  override fun deserialize(decoder: Decoder): Instant {
    return Instant.parse(decoder.decodeString())
  }
}
