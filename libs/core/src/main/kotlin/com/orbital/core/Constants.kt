package com.orbital.core

/** Shared constants for pagination defaults, the API path prefix, and cache TTLs. */
object Constants {
  /** Default page size for [PagedResponse]-shaped endpoints that don't specify one. */
  const val DEFAULT_PAGE_SIZE = 20

  /** Default (zero-based) page index for [PagedResponse]-shaped endpoints. */
  const val DEFAULT_PAGE = 0

  /** Common URL prefix every backend route is mounted under. */
  const val API_VERSION_PREFIX = "/api/v1"

  /** How long the gateway may cache a market price response before refetching. */
  const val MARKET_PRICE_CACHE_TTL_SECONDS = 30L

  /** How long the gateway may cache a market stats response before refetching. */
  const val MARKET_STATS_CACHE_TTL_SECONDS = 60L

  /** How long the gateway may cache a news response before refetching. */
  const val NEWS_CACHE_TTL_SECONDS = 300L
}
