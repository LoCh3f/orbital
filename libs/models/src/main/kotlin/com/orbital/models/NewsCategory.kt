package com.orbital.models

import kotlinx.serialization.Serializable

/**
 * News topic category, matched case-insensitively against the `?category=` query param on `GET
 * /api/v1/news` (defaulting to [CRYPTO] when omitted or unrecognized).
 */
@Serializable
enum class NewsCategory {
  CRYPTO,
  MARKETS,
  MACRO,
  TECH
}
