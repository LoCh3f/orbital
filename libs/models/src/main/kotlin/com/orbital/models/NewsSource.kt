package com.orbital.models

import kotlinx.serialization.Serializable

@Serializable
data class NewsSource(val id: String, val name: String, val url: String, val language: String)
