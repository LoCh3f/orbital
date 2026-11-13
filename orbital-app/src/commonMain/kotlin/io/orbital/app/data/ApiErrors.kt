package io.orbital.app.data

import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

internal fun HttpResponse.checked(): HttpResponse {
  check(status.isSuccess()) { "Service returned ${status.value}. Please retry shortly." }
  return this
}
