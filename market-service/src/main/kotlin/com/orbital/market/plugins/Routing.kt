@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package com.orbital.market.plugins

import com.orbital.core.ApiResponse
import com.orbital.core.ExternalApiException
import com.orbital.core.NotFoundException
import com.orbital.market.api.CoinGeckoClient
import com.orbital.market.api.CoinGeckoMapper
import com.orbital.market.history.HistoryQueries
import com.orbital.market.history.HistoryRepository
import com.orbital.market.persistence.MarketRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TOP_LIMIT = 20

/**
 * Registers `/health`, `/api/v1/market/prices`, `/api/v1/market/prices/{coinId}`,
 * `/api/v1/market/stats/{coinId}`, and `/api/v1/market/history/{coinId}`.
 */
@Suppress("UnusedParameter") // Retained for source compatibility with existing callers.
fun Application.configureRouting(
    appScope: CoroutineScope,
    coinGeckoClient: CoinGeckoClient =
        CoinGeckoClient(
            HttpClient(CIO) {
              install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
              }
              install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            })
) {
  routing {
    healthRoute()
    pricesRoute(coinGeckoClient)
    priceRoute(coinGeckoClient)
    statsRoute(coinGeckoClient)
    historyRoute()
    get("/api/v1/market/history-range/{coinId}") {
      val coin = call.parameters["coinId"] ?: throw IllegalArgumentException("Missing coin ID")
      val params = call.request.queryParameters
      fun bound(name: String): Long? =
          params[name]?.let {
            it.toLongOrNull() ?: throw IllegalArgumentException("Invalid history bound")
          }
      val series =
          withContext(Dispatchers.IO) {
            HistoryQueries.query(
                coin,
                params["source"] ?: "auto",
                bound("from"),
                bound("to"),
                params["range"] ?: "all")
          }
      call.respond(ApiResponse.Success(series))
    }
    get("/api/v1/market/history-points/{coinId}") {
      val coinId = call.parameters["coinId"] ?: throw IllegalArgumentException("Missing coin ID")
      call.respond(ApiResponse.Success(MarketRepository.getTimestampedHistory(coinId)))
    }
  }
}

private fun Route.healthRoute() {
  get("/health") { call.respond(mapOf("status" to "healthy", "service" to "market")) }
}

/**
 * `GET /api/v1/market/prices` — top [TOP_LIMIT] coins by market cap, or exactly the coins named by
 * a comma-separated `coinIds` query param (regardless of their rank). The ingestion worker persists
 * snapshots to Postgres independently of this read-only endpoint.
 */
private fun Route.pricesRoute(coinGeckoClient: CoinGeckoClient) {

  get("/api/v1/market/prices") {
    val coinIds =
        call.request.queryParameters["coinIds"]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
    val prices = runCatching {
      if (coinIds.isEmpty()) coinGeckoClient.getTopCryptos(TOP_LIMIT)
      else coinGeckoClient.getCryptosByIds(coinIds)
    }
    if (prices.isFailure) {
      respondWithError(call, prices.exceptionOrNull(), "Failed to fetch prices")
      return@get
    }

    val payload = prices.getOrThrow().map(CoinGeckoMapper::toCoinPrice)

    if (coinIds.isEmpty())
        runCatching {
          withContext(Dispatchers.IO) {
            HistoryRepository.track(
                payload.map { it.coinId },
                payload.associate { it.coinId to (it.symbol to it.name) })
          }
        }
    call.respond(ApiResponse.Success(payload))
  }
}

/** `GET /api/v1/market/prices/{coinId}` — a single coin's price, or 404 if unknown. */
private fun Route.priceRoute(coinGeckoClient: CoinGeckoClient) {
  get("/api/v1/market/prices/{coinId}") {
    val coinId = call.parameters["coinId"] ?: throw IllegalArgumentException("Missing coin ID")
    val result = runCatching {
      val asset = CoinGeckoMapper.requireFound(coinGeckoClient.getCryptoDetails(coinId), coinId)
      CoinGeckoMapper.toCoinPrice(asset)
    }
    if (result.isFailure) {
      respondWithError(call, result.exceptionOrNull(), "Failed to fetch price")
      return@get
    }
    call.respond(ApiResponse.Success(result.getOrThrow()))
  }
}

/** `GET /api/v1/market/stats/{coinId}` — extended stats (ATH/ATL, supply) for a single coin. */
private fun Route.statsRoute(coinGeckoClient: CoinGeckoClient) {
  get("/api/v1/market/stats/{coinId}") {
    val coinId = call.parameters["coinId"] ?: throw IllegalArgumentException("Missing coin ID")
    val result = runCatching {
      val asset = CoinGeckoMapper.requireFound(coinGeckoClient.getCryptoDetails(coinId), coinId)
      CoinGeckoMapper.toMarketStats(asset)
    }
    if (result.isFailure) {
      respondWithError(call, result.exceptionOrNull(), "Failed to fetch stats")
      return@get
    }
    call.respond(ApiResponse.Success(result.getOrThrow()))
  }
}

/**
 * `GET /api/v1/market/history/{coinId}` — stored price snapshots for a coin, oldest first, from
 * [MarketRepository] (our own ingested history, not CoinGecko's 7-day-capped sparkline).
 */
private fun Route.historyRoute() {
  get("/api/v1/market/history/{coinId}") {
    val coinId = call.parameters["coinId"] ?: throw IllegalArgumentException("Missing coin ID")
    val result = runCatching { MarketRepository.getPriceHistory(coinId) }
    if (result.isFailure) {
      respondWithError(call, result.exceptionOrNull(), "Failed to fetch price history")
      return@get
    }
    call.respond(ApiResponse.Success(result.getOrThrow()))
  }
}

private suspend fun respondWithError(
    call: io.ktor.server.application.ApplicationCall,
    error: Throwable?,
    fallbackMessage: String
) {
  val status =
      when (error) {
        is NotFoundException -> HttpStatusCode.NotFound
        is ExternalApiException -> HttpStatusCode.BadGateway
        else -> HttpStatusCode.InternalServerError
      }
  call.respond(status, ApiResponse.Error(status.value, fallbackMessage))
}

private const val REQUEST_TIMEOUT_MS = 10_000L
private const val CONNECT_TIMEOUT_MS = 3_000L
