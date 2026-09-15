package com.orbital.core

import kotlinx.serialization.Serializable

@Serializable
data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
