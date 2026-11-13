@file:Suppress("TooGenericExceptionCaught", "MagicNumber")

package com.orbital.market

import com.orbital.market.api.CoinGeckoClient
import com.orbital.market.api.CoinGeckoMapper
import com.orbital.market.history.HistoryQueue
import com.orbital.market.history.HistoryRepository
import com.orbital.market.history.HistoryWorker
import com.orbital.market.persistence.MarketRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val DEFAULT_INGEST_INTERVAL_MS = 900_000L
private const val TOP_LIMIT = 20
private const val DB_RETRY_BASE_MS = 2_000L
private const val DB_RETRY_MAX_MS = 30_000L

private val logger = LoggerFactory.getLogger("MarketIngestor")

/**
 * Standalone worker, separate from market-service's HTTP API, that polls CoinGecko for the top
 * [TOP_LIMIT] coins and persists a price snapshot to Postgres on a fixed interval. Runs independent
 * of API traffic/replicas, so ingestion cadence doesn't depend on clients polling the API and isn't
 * duplicated if the API is scaled out. Unlike the API server's DB init, the connection here is not
 * optional — this process exists only to write to the DB — so it retries with backoff instead of
 * continuing without one.
 *
 * The same tracked coins receive durable background history jobs. Binance archives supply the
 * available USDT pair history; CoinGecko USD observations remain a separate, growing series.
 */
fun main() = runBlocking {
  val jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://127.0.0.1:5432/orbital"
  val dbUser = System.getenv("DB_USER") ?: "orbital"
  val dbPass = System.getenv("DB_PASSWORD") ?: "orbital"
  val intervalMs = System.getenv("INGEST_INTERVAL_MS")?.toLongOrNull() ?: DEFAULT_INGEST_INTERVAL_MS

  connectWithRetry(jdbcUrl, dbUser, dbPass)

  val coinGeckoClient =
      CoinGeckoClient(
          HttpClient(CIO) {
            install(HttpTimeout) {
              requestTimeoutMillis = 20000
              connectTimeoutMillis = 5000
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          })

  launch { HistoryWorker(coinGeckoClient).run() }

  logger.info("Starting market ingestion loop, interval=${intervalMs}ms")
  while (true) {
    try {
      val topCoins = coinGeckoClient.getTopCryptos(TOP_LIMIT)
      val prices = topCoins.map(CoinGeckoMapper::toCoinPrice)
      withContext(Dispatchers.IO) {
        HistoryRepository.track(
            topCoins.map { it.id }, prices.associate { it.coinId to (it.symbol to it.name) })
      }
      MarketRepository.saveAll(prices, (intervalMs / 1000).coerceAtLeast(1))
      for (coin in topCoins) {
        if (withContext(Dispatchers.IO) {
          HistoryRepository.pair(coin.id) == null && HistoryRepository.pairLookupDue(coin.id)
        }) {
          val pair = verifiedPairOrNull(coinGeckoClient, coin.id)
          if (pair != null) withContext(Dispatchers.IO) { HistoryRepository.setPair(coin.id, pair) }
          delay(4000)
        }
        withContext(Dispatchers.IO) {
          if (HistoryRepository.pair(coin.id) != null)
              HistoryQueue.enqueue(
                  coin.id, "binance", 1501545600L, java.time.Instant.now().epochSecond, 86400L, 0)
          HistoryQueue.enqueue(
              coin.id,
              "coingecko",
              java.time.Instant.now().epochSecond - 31536000L,
              java.time.Instant.now().epochSecond,
              86400L,
              0)
        }
      }
      logger.info("Persisted ${prices.size} price snapshots")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logger.warn("Ingestion cycle failed", e)
    }
    delay(intervalMs)
  }
}

/** Retries [MarketRepository.initDatabase] with capped linear backoff until it succeeds. */
private suspend fun connectWithRetry(jdbcUrl: String, user: String, password: String) {
  var attempt = 0
  while (true) {
    try {
      MarketRepository.initDatabase(jdbcUrl, user, password)
      return
    } catch (e: Exception) {
      attempt++
      val backoffMs = (DB_RETRY_BASE_MS * attempt).coerceAtMost(DB_RETRY_MAX_MS)
      logger.warn("DB connection failed (attempt $attempt), retrying in ${backoffMs}ms", e)
      delay(backoffMs)
    }
  }
}

/** A ticker lookup failure must not stop snapshots or other coins' archive imports. */
private suspend fun verifiedPairOrNull(client: CoinGeckoClient, id: String): String? =
    try {
      client.verifiedBinancePair(id)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failure: Exception) {
      logger.warn(
          "Binance identity lookup delayed for coin={}: {}", id, failure.javaClass.simpleName)
      null
    }
