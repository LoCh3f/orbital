package com.orbital.models

import kotlinx.serialization.Serializable

/** A news publisher/outlet. Not currently populated by any endpoint — reserved for future use. */
@Serializable
data class NewsSource(val id: String, val name: String, val url: String, val language: String)
