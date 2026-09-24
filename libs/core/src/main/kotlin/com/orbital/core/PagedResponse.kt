package com.orbital.core

import kotlinx.serialization.Serializable

/**
 * A page of [items] plus the metadata needed to request the next one. Not currently used by any
 * endpoint (all current listing endpoints return a plain [ApiResponse.Success] of the full list),
 * but available for any future paginated endpoint.
 */
@Serializable
data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
