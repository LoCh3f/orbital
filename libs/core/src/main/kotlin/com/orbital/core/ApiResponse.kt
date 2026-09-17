package com.orbital.core

import kotlinx.serialization.Serializable

@Serializable
sealed class ApiResponse<out T> {
  @Serializable data class Success<out T>(val data: T) : ApiResponse<T>()

  @Serializable data class Error(val code: Int, val message: String) : ApiResponse<Nothing>()

  @Serializable data object Loading : ApiResponse<Nothing>()
}
