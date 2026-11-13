package com.orbital.market.history

import com.orbital.market.persistence.MarketRepository
import com.orbital.models.CoinPrice
import com.orbital.models.HistoryValue
import java.sql.DriverManager
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class HistoryRepositoryTest {
  private fun initialize() {
    val postgres = System.getenv("TEST_POSTGRES_URL")
    val url =
        if (postgres == null) {
          "jdbc:h2:mem:history-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        } else {
          val schema = "history_" + System.nanoTime()
          DriverManager.getConnection(postgres).use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
          }
          postgres + "&currentSchema=" + schema
        }
    MarketRepository.initDatabase(
        url, System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")
    HistoryRepository.track(listOf("bitcoin", "unlisted-coin"))
  }

  @Test
  fun `all history includes old observations beyond the former latest 500 cap`() = runBlocking {
    initialize()
    val start = Instant.parse("2018-01-01T00:00:00Z").epochSecond
    HistoryRepository.saveArchive(
        "bitcoin",
        "BTCUSDT",
        86400,
        "fixture",
        "a".repeat(64),
        (0..3000).map { HistoryValue(start + it * 86400, 100.0 + it) })
    val series = HistoryQueries.query("bitcoin", "binance", null, null, "all", start + 3001 * 86400)
    assertEquals(start, series.earliestAvailable)
    assertTrue(series.points.size <= 1501)
    assertTrue(series.points.first().timestamp < start + 7 * 86400)
    assertEquals(start + 3000 * 86400, series.points.last().timestamp)
    assertEquals("USDT", series.quote)
    assertTrue(series.importing)
  }

  @Test
  fun `quotes stay separate and only tracked coins can schedule imports`() = runBlocking {
    initialize()
    MarketRepository.saveAll(
        listOf(
            CoinPrice(
                "bitcoin",
                "BTC",
                "Bitcoin",
                10.0,
                100.0,
                100.0,
                0.0,
                Instant.ofEpochSecond(1700000000))))
    HistoryRepository.saveArchive(
        "bitcoin",
        "BTCUSDT",
        300,
        "fixture",
        "a".repeat(64),
        listOf(HistoryValue(1700000000, 11.0)))
    val usd =
        HistoryQueries.query("bitcoin", "coingecko", 1699999900, 1700000100, "24h", 1700000200)
    val usdt = HistoryQueries.query("bitcoin", "binance", 1699999900, 1700000100, "24h", 1700000200)
    assertEquals(10.0, usd.points.single().price)
    assertEquals("USD", usd.quote)
    assertEquals(11.0, usdt.points.single().price)
    assertEquals("USDT", usdt.quote)
    assertFailsWith<IllegalArgumentException> {
      HistoryQueries.query("not-tracked", "auto", null, null, "all")
    }
    assertFailsWith<IllegalArgumentException> {
      HistoryQueries.query("unlisted-coin", "binance", null, null, "all")
    }
    Unit
  }

  @Test
  fun `gaps are explicit and finer windows return finer samples`() {
    initialize()
    val start = 1700000000L
    HistoryRepository.saveArchive(
        "bitcoin",
        "BTCUSDT",
        300,
        "fixture",
        "a".repeat(64),
        listOf(
            HistoryValue(start, 10.0),
            HistoryValue(start + 300, 11.0),
            HistoryValue(start + 7200, 12.0)))
    val series =
        HistoryQueries.query("bitcoin", "binance", start - 1, start + 7201, "24h", start + 7300)
    assertEquals(1, series.gaps.size)
    assertEquals(300L, series.resolutionSeconds)
    assertTrue(HistoryQueries.resolution(31536000) > series.resolutionSeconds)
    assertFailsWith<IllegalArgumentException> {
      HistoryQueries.query("bitcoin", "auto", 100, 1, "24h")
    }
  }

  @Test
  fun `completed files are idempotent and inactive coins do not import`() {
    initialize()
    HistoryRepository.saveArchive(
        "bitcoin",
        "BTCUSDT",
        86400,
        "fixture",
        "a".repeat(64),
        listOf(HistoryValue(1700000000, 10.0)))
    HistoryRepository.saveArchive(
        "bitcoin",
        "BTCUSDT",
        86400,
        "fixture",
        "a".repeat(64),
        listOf(HistoryValue(1700000000, 20.0)))
    HistoryQueue.enqueue("bitcoin", "binance", 1501545600, 1700000000, 86400)
    HistoryRepository.track(listOf("unlisted-coin"))
    assertEquals(null, HistoryQueue.nextJob())
    assertTrue(HistoryRepository.imported("fixture"))
  }

  @Test
  fun `a failed priority job yields to other tracked coins and survives refresh`() {
    initialize()
    HistoryQueue.enqueue("bitcoin", "binance", 1501545600, 1700000000, 86400, 1)
    HistoryQueue.enqueue("unlisted-coin", "coingecko", 1690000000, 1700000000, 86400, 0)
    val failed = HistoryQueue.nextJob()!!
    assertEquals("bitcoin", failed.coinId)
    HistoryQueue.retry(failed)
    HistoryQueue.enqueue("bitcoin", "binance", 1501545600, 1700000000, 86400, 1)
    assertEquals("unlisted-coin", HistoryQueue.nextJob()!!.coinId)
  }

  @Test
  fun `coin universe stays unchanged and unsupported pair lookups are bounded`() {
    initialize()
    assertTrue(HistoryRepository.pairLookupDue("unlisted-coin"))
    assertEquals(false, HistoryRepository.pairLookupDue("unlisted-coin"))
    assertEquals(null, HistoryRepository.pair("ethereum"))
    val fallback = HistoryQueries.query("unlisted-coin", "auto", null, null, "all")
    assertEquals("coingecko", fallback.source)
    assertEquals(false, fallback.binanceAvailable)
    assertTrue(fallback.notice!!.contains("No verified Binance"))
    HistoryRepository.track(listOf("unlisted-coin"))
    assertFailsWith<IllegalArgumentException> {
      HistoryQueries.query("bitcoin", "auto", null, null, "all")
    }
  }

  @Test
  fun `one missing candle is reported without flagging a complete coarse series`() {
    initialize()
    val start = 1700000000L
    HistoryRepository.saveArchive(
        "bitcoin",
        "BTCUSDT",
        300,
        "fixture",
        "a".repeat(64),
        listOf(HistoryValue(start, 10.0), HistoryValue(start + 600, 11.0)))
    val result = HistoryQueries.query("bitcoin", "binance", start, start + 601, "24h", start + 602)
    assertEquals(start + 300, result.gaps.single().from)
    assertEquals(start + 300, result.gaps.single().to)
  }
}
