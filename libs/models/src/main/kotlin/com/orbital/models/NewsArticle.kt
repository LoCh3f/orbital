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
