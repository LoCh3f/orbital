package com.orbital.models

import kotlinx.serialization.Serializable

@Serializable
enum class NewsCategory {
  CRYPTO,
  MARKETS,
  MACRO,
  TECH
}
