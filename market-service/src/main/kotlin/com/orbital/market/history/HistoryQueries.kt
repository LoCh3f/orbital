@file:Suppress("MagicNumber", "LongParameterList")

package com.orbital.market.history

import com.orbital.models.HistoryGap
import com.orbital.models.HistorySeries
import com.orbital.models.HistoryValue
import java.sql.Connection
import java.time.Instant
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

private data class HistoryStorage(val binance: Boolean) {
  val table = if (binance) "history_candles" else "market_prices"
  val time = if (binance) "timestamp" else "last_updated"
  val price = if (binance) "price" else "current_price_usd"
  val cadence = if (binance) "interval_seconds" else "sample_interval_seconds"
}

private data class QueryWindow(val from: Long, val to: Long, val step: Long)

private data class Samples(val points: List<HistoryValue>, val cadences: List<Long>)

object HistoryQueries {
  fun query(
      coin: String,
      source: String,
      from: Long?,
      to: Long?,
      range: String,
      now: Long = Instant.now().epochSecond
  ): HistorySeries = transaction {
    require(source in setOf("auto", "binance", "coingecko")) { "Unknown history source" }
    val tracked =
        HistoryCoins.select { (HistoryCoins.coinId eq coin) and (HistoryCoins.active eq true) }
            .firstOrNull() ?: throw IllegalArgumentException("Coin is not currently tracked")
    val pair = tracked[HistoryCoins.pair]
    val actualSource =
        if (source == "auto") {
          if (pair != null) "binance" else "coingecko"
        } else source
    val storage = HistoryStorage(actualSource == "binance")
    require(!storage.binance || pair != null) { "No verified Binance pair for this coin" }
    val connection = connection.connection as Connection
    val bounds = bounds(connection, storage, coin)
    val window = window(from, to, range, bounds, now)
    val samples = samples(connection, storage, coin, window)
    val allHistory = range == "all" && from == null
    val archiveStep = if (allHistory) 86400L else archiveResolution(window.step)
    HistoryQueue.enqueue(
        coin,
        actualSource,
        if (allHistory) 1501545600L else window.from,
        if (allHistory) now else window.to,
        archiveStep)
    val pending =
        !HistoryJobs.select {
              (HistoryJobs.coinId eq coin) and
                  (HistoryJobs.provider eq actualSource) and
                  (HistoryJobs.state neq "complete")
            }
            .empty()
    val notice =
        if (pair == null) "No verified Binance listing. Showing available CoinGecko USD history."
        else null
    HistorySeries(
        coin,
        actualSource,
        if (storage.binance) "USDT" else "USD",
        if (storage.binance) pair else null,
        samples.points,
        window.from,
        window.to,
        maxOf(window.step, samples.cadences.minOrNull() ?: window.step),
        bounds.first,
        bounds.second,
        gaps(samples, window.step),
        pending,
        notice,
        pair != null)
  }

  private fun bounds(
      connection: Connection,
      storage: HistoryStorage,
      coin: String
  ): Pair<Long?, Long?> =
      connection
          .prepareStatement(
              "SELECT MIN(${storage.time}), MAX(${storage.time}) FROM ${storage.table} WHERE coin_id = ?")
          .use { statement ->
            statement.setString(1, coin)
            statement.executeQuery().use { rs ->
              rs.next()
              val first = rs.getLong(1).takeUnless { rs.wasNull() }
              first to rs.getLong(2).takeUnless { rs.wasNull() }
            }
          }

  private fun window(
      from: Long?,
      to: Long?,
      range: String,
      bounds: Pair<Long?, Long?>,
      now: Long
  ): QueryWindow {
    require(range in setOf("24h", "7d", "1m", "1y", "all")) { "Unknown history range" }
    require((from == null) == (to == null)) { "Specify both from and to" }
    val duration = rangeDuration(range)
    val end =
        to
            ?: if (range == "all") (bounds.second ?: now).coerceAtLeast((bounds.first ?: 0) + 1)
            else now
    val defaultStart = duration?.let { (end - it).coerceAtLeast(0) } ?: bounds.first ?: 0
    val start = from ?: defaultStart
    require(start >= 0 && end > start && end <= now + 86400) { "Invalid history bounds" }
    return QueryWindow(start, end, resolution(end - start))
  }

  private fun rangeDuration(range: String): Long? =
      when (range) {
        "24h" -> 86400L
        "7d" -> 604800L
        "1m" -> 2592000L
        "1y" -> 31536000L
        else -> null
      }

  private fun samples(
      connection: Connection,
      storage: HistoryStorage,
      coin: String,
      window: QueryWindow
  ): Samples {
    val values = mutableListOf<HistoryValue>()
    val cadences = mutableListOf<Long>()
    val sql =
        """SELECT t, p, cadence FROM (
      SELECT ${storage.time} AS t, ${storage.price} AS p, ${storage.cadence} AS cadence,
      ROW_NUMBER() OVER (PARTITION BY FLOOR(CAST(${storage.time} AS DECIMAL(20,0)) / ?)
        ORDER BY ${storage.time} DESC, ${storage.cadence} ASC) AS rn
      FROM ${storage.table} WHERE coin_id = ? AND ${storage.time} >= ? AND ${storage.time} <= ?
    ) ranked WHERE rn = 1 ORDER BY t"""
    connection.prepareStatement(sql).use { statement ->
      statement.setLong(1, window.step)
      statement.setString(2, coin)
      statement.setLong(3, window.from)
      statement.setLong(4, window.to)
      statement.executeQuery().use { rs ->
        while (rs.next()) {
          values += HistoryValue(rs.getLong(1), rs.getDouble(2))
          cadences += rs.getLong(3)
        }
      }
    }
    return Samples(values, cadences)
  }

  private fun gaps(samples: Samples, step: Long): List<HistoryGap> =
      samples.points.zipWithNext().mapIndexedNotNull { index, (a, b) ->
        val expected = maxOf(step, samples.cadences[index], samples.cadences[index + 1])
        if (b.timestamp - a.timestamp > expected + expected / 2)
            HistoryGap(a.timestamp + expected, b.timestamp - expected)
        else null
      }

  private fun archiveResolution(step: Long): Long =
      when {
        step < 3600 -> 300L
        step < 86400 -> 3600L
        else -> 86400L
      }

  fun resolution(span: Long): Long =
      listOf(300L, 900L, 3600L, 14400L, 86400L, 604800L, 2592000L).firstOrNull { span / it <= 1500 }
          ?: (span + 1499) / 1500
}
