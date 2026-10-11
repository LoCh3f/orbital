@file:Suppress("TooGenericExceptionCaught")

package com.orbital.market

import com.orbital.market.api.CoinGeckoClient
import com.orbital.market.api.CoinGeckoCryptoData
import com.orbital.market.api.CoinGeckoMapper
import com.orbital.market.persistence.MarketRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val DEFAULT_INGEST_INTERVAL_MS = 60_000L
private const val TOP_LIMIT = 20
private const val DB_RETRY_BASE_MS = 2_000L
private const val DB_RETRY_MAX_MS = 30_000L

// Spacing between per-coin market_chart calls during backfill, to stay well under CoinGecko's
// public-tier rate limit (one call per coin, run once per coin, ever — not on the polling loop).
private const val BACKFILL_CALL_SPACING_MS = 4_000L

private val logger = LoggerFactory.getLogger("MarketIngestor")

/**
 * Standalone worker, separate from market-service's HTTP API, that polls CoinGecko for the top
 * [TOP_LIMIT] coins and persists a price snapshot to Postgres on a fixed interval. Runs independent
 * of API traffic/replicas, so ingestion cadence doesn't depend on clients polling the API and isn't
 * duplicated if the API is scaled out. Unlike the API server's DB init, the connection here is not
 * optional — this process exists only to write to the DB — so it retries with backoff instead of
 * continuing without one.
 *
 * Before entering the polling loop, [backfillIfNeeded] is run once per coin: a coin seen for the
 * first time gets CoinGecko's full historical `market_chart` loaded into the same table, so the
 * detail chart has long history immediately instead of waiting weeks for the polling loop alone to
 * build it up. Already-backfilled coins are skipped on every later restart.
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
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          })

  val topCoins =
      runCatching { coinGeckoClient.getTopCryptos(TOP_LIMIT) }
          .getOrElse { e ->
            logger.warn("Could not list top coins for backfill, skipping it this run", e)
            emptyList()
          }
  for (coin in topCoins) {
    backfillIfNeeded(coinGeckoClient, coin)
  }

  logger.info("Starting market ingestion loop, interval=${intervalMs}ms")
  while (true) {
    try {
      val prices = coinGeckoClient.getTopCryptos(TOP_LIMIT).map(CoinGeckoMapper::toCoinPrice)
      MarketRepository.saveAll(prices)
      logger.info("Persisted ${prices.size} price snapshots")
    } catch (e: Exception) {
      logger.warn("Ingestion cycle failed", e)
    }
    delay(intervalMs)
  }
}

/**
 * Loads [coin]'s full `market_chart` history into [MarketRepository] if it has none stored yet — a
 * no-op, with no CoinGecko call or delay, once that's already true. Waits
 * [BACKFILL_CALL_SPACING_MS] after an actual fetch (success or failure) to keep a run across many
 * not-yet-backfilled coins spaced out.
 */
private suspend fun backfillIfNeeded(client: CoinGeckoClient, coin: CoinGeckoCryptoData) {
  if (MarketRepository.hasHistory(coin.id)) return
  try {
    val chart = client.getMarketChart(coin.id)
    val snapshots = CoinGeckoMapper.toHistorySnapshots(coin.id, coin.symbol, coin.name, chart)
    MarketRepository.saveAll(snapshots)
    logger.info("Backfilled ${snapshots.size} historical snapshots for '${coin.id}'")
  } catch (e: Exception) {
    logger.warn("Backfill failed for '${coin.id}'", e)
  }
  delay(BACKFILL_CALL_SPACING_MS)
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
