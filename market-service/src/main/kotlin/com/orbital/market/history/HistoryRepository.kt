@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "LongParameterList")

package com.orbital.market.history

import com.orbital.models.HistoryValue
import java.security.MessageDigest
import java.time.Instant
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object HistoryCandles : Table("history_candles") {
  val id = varchar("id", 64)
  val coinId = varchar("coin_id", 64)
  val pair = varchar("pair", 32)
  val timestamp = long("timestamp")
  val price = double("price")
  val interval = long("interval_seconds")
  override val primaryKey = PrimaryKey(id)

  init {
    index(false, coinId, timestamp)
  }
}

object HistoryCoins : Table("history_coins") {
  val coinId = varchar("coin_id", 64)
  val symbol = varchar("symbol", 16).default("")
  val name = varchar("name", 128).default("")
  val pairChecked = long("pair_checked").default(0)
  val active = bool("active")
  val pair = varchar("pair", 32).nullable()
  override val primaryKey = PrimaryKey(coinId)
}

object HistoryJobs : Table("history_jobs") {
  val id = varchar("id", 64)
  val coinId = varchar("coin_id", 64)
  val provider = varchar("source", 16)
  val from = long("from_time")
  val to = long("to_time")
  val step = long("step_seconds")
  val priority = integer("priority")
  val state = varchar("state", 16)
  val updated = long("updated")
  override val primaryKey = PrimaryKey(id)
}

object HistoryFiles : Table("history_files") {
  val path = varchar("path", 256)
  val checksum = varchar("checksum", 64)
  override val primaryKey = PrimaryKey(path)
}

data class ArchiveJob(
    val id: String,
    val coinId: String,
    val source: String,
    val from: Long,
    val to: Long,
    val step: Long
)

/** Uses the same transaction/database as market persistence, preserving the existing archive. */
object HistoryRepository {
  fun initialize() {
    SchemaUtils.createMissingTablesAndColumns(
        HistoryCandles, HistoryCoins, HistoryJobs, HistoryFiles)
  }

  fun track(ids: List<String>, metadata: Map<String, Pair<String, String>> = emptyMap()) =
      transaction {
        HistoryCoins.update { it[active] = false }
        ids.forEach { id ->
          if (HistoryCoins.select { HistoryCoins.coinId eq id }.empty()) {
            HistoryCoins.insert {
              it[coinId] = id
              it[active] = true
              it[pair] = BinancePairs.known[id]
              it[symbol] = metadata[id]?.first?.take(16).orEmpty()
              it[name] = metadata[id]?.second?.take(128).orEmpty()
            }
          } else
              HistoryCoins.update({ HistoryCoins.coinId eq id }) {
                it[active] = true
                metadata[id]?.let { data ->
                  it[symbol] = data.first.take(16)
                  it[name] = data.second.take(128)
                }
              }
        }
      }

  fun pair(coin: String): String? = transaction {
    HistoryCoins.select { HistoryCoins.coinId eq coin }.firstOrNull()?.get(HistoryCoins.pair)
  }

  fun setPair(coin: String, symbol: String) = transaction {
    require(symbol.matches(Regex("[A-Z0-9]{4,32}")))
    HistoryCoins.update({ HistoryCoins.coinId eq coin }) { it[pair] = symbol }
  }

  /** Unsupported assets are checked at most daily, preserving the free API request budget. */
  fun pairLookupDue(coin: String): Boolean = transaction {
    val now = Instant.now().epochSecond
    HistoryCoins.update({
      (HistoryCoins.coinId eq coin) and (HistoryCoins.pairChecked lessEq now - 86400)
    }) {
      it[pairChecked] = now
    } > 0
  }

  fun imported(path: String): Boolean = transaction {
    !HistoryFiles.select { HistoryFiles.path eq path }.empty()
  }

  fun saveArchive(
      coin: String,
      pair: String,
      step: Long,
      path: String,
      checksum: String,
      values: List<HistoryValue>
  ) = transaction {
    if (!HistoryFiles.select { HistoryFiles.path eq path }.empty()) return@transaction
    val firstTime = values.minOfOrNull { it.timestamp } ?: 0L
    val lastTime = values.maxOfOrNull { it.timestamp } ?: 0L
    val existing =
        HistoryCandles.select {
              (HistoryCandles.coinId eq coin) and
                  (HistoryCandles.interval eq step) and
                  (HistoryCandles.timestamp greaterEq firstTime) and
                  (HistoryCandles.timestamp lessEq lastTime)
            }
            .map { it[HistoryCandles.id] }
            .toHashSet()
    values.forEach { point ->
      val pointId = historyDigest("$coin:$pair:$step:${point.timestamp}")
      if (!existing.add(pointId)) return@forEach
      require(point.timestamp > 0 && point.price.isFinite() && point.price > 0)
      HistoryCandles.insert {
        it[id] = pointId
        it[coinId] = coin
        it[HistoryCandles.pair] = pair
        it[timestamp] = point.timestamp
        it[price] = point.price
        it[interval] = step
      }
    }
    HistoryFiles.insert {
      it[HistoryFiles.path] = path
      it[HistoryFiles.checksum] = checksum
    }
  }

  fun metadata(coin: String): Pair<String, String> = transaction {
    val row = HistoryCoins.select { HistoryCoins.coinId eq coin }.first()
    row[HistoryCoins.symbol].ifBlank { coin.take(16) } to
        row[HistoryCoins.name].ifBlank { coin.take(128) }
  }
}

internal fun historyDigest(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
      "%02x".format(it)
    }
