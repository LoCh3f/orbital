package com.orbital.core

import kotlinx.serialization.Serializable

/**
 * Standard response envelope every service endpoint returns. Consumers decode into the concrete
 * variant they expect (usually [Success]) rather than the sealed type, so no `type` discriminator
 * is ever present on the wire — see the gateway/README notes on why proxied responses are just
 * `{"data": ...}` or `{"code": ..., "message": ...}`.
 */
@Serializable
sealed class ApiResponse<out T> {
  /** A successful response wrapping the actual [data] payload. */
  @Serializable data class Success<out T>(val data: T) : ApiResponse<T>()

  /** An error response with an HTTP-style [code] and a human-readable [message]. */
  @Serializable data class Error(val code: Int, val message: String) : ApiResponse<Nothing>()

  /** Reserved for clients that want an explicit in-flight state; no server endpoint emits this. */
  @Serializable data object Loading : ApiResponse<Nothing>()
}
