package com.orbital.models

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A single news article, as returned by `GET /api/v1/news`. [id] is freshly generated on every
 * fetch (see `NewsMapper.toDomain`), so repeated fetches of the same underlying article produce
 * distinct rows when persisted — this is an accepted append-only design, not a bug.
 */
@Serializable
data class NewsArticle(
    @Serializable(with = UuidSerializer::class) val id: UUID,
    val title: String,
    val description: String,
    val url: String,
    val sourceName: String,
    @Serializable(with = InstantSerializer::class) val publishedAt: Instant,
    val category: NewsCategory
)

/** Serializes [UUID] as a plain string rather than kotlinx.serialization's default. */
object UuidSerializer : KSerializer<UUID> {
  override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: UUID) {
    encoder.encodeString(value.toString())
  }

  override fun deserialize(decoder: Decoder): UUID {
    return UUID.fromString(decoder.decodeString())
  }
}
