@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package com.orbital.market.plugins

import com.orbital.core.ApiResponse
import com.orbital.core.ExternalApiException
import com.orbital.core.NotFoundException
import com.orbital.market.api.CoinGeckoClient
import com.orbital.market.api.CoinGeckoMapper
import com.orbital.market.persistence.MarketRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val TOP_LIMIT = 20

fun Application.configureRouting(
    appScope: CoroutineScope,
    coinGeckoClient: CoinGeckoClient =
        CoinGeckoClient(
            HttpClient(CIO) {
              install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            })
) {
  routing {
    healthRoute()
    pricesRoute(coinGeckoClient, appScope)
    priceRoute(coinGeckoClient)
    statsRoute(coinGeckoClient)
  }
}

private fun Route.healthRoute() {
  get("/health") { call.respond(mapOf("status" to "healthy", "service" to "market")) }
}

private fun Route.pricesRoute(coinGeckoClient: CoinGeckoClient, appScope: CoroutineScope) {
  val logger = LoggerFactory.getLogger("MarketRouting")

  get("/api/v1/market/prices") {
    val coinIds =
        call.request.queryParameters["coinIds"]
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
    val prices = runCatching {
      val fullList = coinGeckoClient.getTopCryptos(TOP_LIMIT)
      if (coinIds.isEmpty()) fullList else fullList.filter { it.id in coinIds }
    }
    if (prices.isFailure) {
      respondWithError(call, prices.exceptionOrNull(), "Failed to fetch prices")
      return@get
    }

    val payload = prices.getOrThrow().map(CoinGeckoMapper::toCoinPrice)

    // Persist fetched snapshots asynchronously (best-effort) using application scope
    appScope.launch {
      try {
        MarketRepository.saveAll(payload)
      } catch (e: Exception) {
        logger.warn("Failed to persist market prices", e)
      }
    }

    call.respond(ApiResponse.Success(payload))
  }
}

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
  call.respond(status, ApiResponse.Error(status.value, error?.message ?: fallbackMessage))
}
