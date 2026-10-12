@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "MagicNumber")

package com.orbital.market.persistence

import com.orbital.models.CoinPrice
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/** Caps how many stored snapshots [MarketRepository.getPriceHistory] returns for one coin. */
private const val MAX_HISTORY_POINTS = 500

/**
 * Append-only log of price snapshots — every fetch inserts fresh rows (own `id` per row, no
 * upsert/dedup), so this is a time series of observations, not a keyed "current price" table.
 */
object MarketPriceTable : Table("market_prices") {
  val id: Column<String> = varchar("id", 36)
  val coinId: Column<String> = varchar("coin_id", 64)
  val symbol: Column<String> = varchar("symbol", 16)
  val name: Column<String> = varchar("name", 128)
  val currentPriceUsd: Column<Double> = double("current_price_usd")
  val marketCapUsd: Column<Double> = double("market_cap_usd")
  val volume24hUsd: Column<Double> = double("volume_24h_usd")
  val priceChangePercent24h: Column<Double> = double("price_change_percent_24h")
  val lastUpdated: Column<Long> = long("last_updated")
  override val primaryKey = PrimaryKey(id)

  init {
    // getPriceHistory filters by coinId and orders by lastUpdated — without this, that's a full
    // table scan on a table that only ever grows (append-only, plus a one-time per-coin backfill
    // of possibly thousands of rows).
    index(isUnique = false, columns = arrayOf(coinId, lastUpdated))
  }
}

/** Exposed/HikariCP-backed persistence for [MarketPriceTable]. */
object MarketRepository {
  private val logger = LoggerFactory.getLogger("MarketRepository")

  /** Connects to Postgres and creates [MarketPriceTable] if it doesn't already exist. */
  fun initDatabase(jdbcUrl: String, user: String, password: String) {
    val config =
        HikariConfig().apply {
          this.jdbcUrl = jdbcUrl
          this.username = user
          this.password = password
          maximumPoolSize = 3
          isAutoCommit = false
          transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
    val ds = HikariDataSource(config)
    Database.connect(ds)
    transaction { SchemaUtils.createMissingTablesAndColumns(MarketPriceTable) }
  }

  /**
   * Inserts one row per price inside a single transaction, catching and logging (never throwing)
   * any per-row failure. On H2 (used in tests), execution continues past a failed statement; on
   * Postgres, a failed insert aborts the whole surrounding transaction, so in production a single
   * bad row can take the rest of the batch down with it — the caller's own catch still prevents
   * this from failing the response, but rows are not independently committed.
   */
  suspend fun saveAll(prices: List<CoinPrice>) =
      withContext(Dispatchers.IO) {
        transaction {
          for (p in prices) {
            try {
              MarketPriceTable.insert {
                it[id] = java.util.UUID.randomUUID().toString()
                it[coinId] = p.coinId
                it[symbol] = p.symbol
                it[name] = p.name
                it[currentPriceUsd] = p.currentPriceUsd
                it[marketCapUsd] = p.marketCapUsd
                it[volume24hUsd] = p.volume24hUsd
                it[priceChangePercent24h] = p.priceChangePercent24h
                it[lastUpdated] = p.lastUpdated.epochSecond
              }
            } catch (e: Exception) {
              logger.warn("Failed to persist price snapshot for coinId='${p.coinId}'", e)
            }
          }
        }
      }

  /**
   * Returns up to [MAX_HISTORY_POINTS] stored price snapshots for [coinId], oldest first — the most
   * recent [MAX_HISTORY_POINTS] rows, since this table only grows and an unbounded scan would get
   * slower (and the response bigger) over time.
   */
  suspend fun getPriceHistory(coinId: String): List<Double> =
      withContext(Dispatchers.IO) {
        transaction {
          MarketPriceTable.select { MarketPriceTable.coinId eq coinId }
              .orderBy(MarketPriceTable.lastUpdated, SortOrder.DESC)
              .limit(MAX_HISTORY_POINTS)
              .map { it[MarketPriceTable.currentPriceUsd] }
              .reversed()
        }
      }

  /** Whether any snapshot (live or backfilled) has been stored for [coinId] yet. */
  suspend fun hasHistory(coinId: String): Boolean =
      withContext(Dispatchers.IO) {
        transaction {
          MarketPriceTable.select { MarketPriceTable.coinId eq coinId }.limit(1).count() > 0
        }
      }
}
