package com.orbital.market.persistence

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.orbital.models.CoinPrice
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class MarketRepositoryTest {
  private fun testDbUrl() =
      "jdbc:h2:mem:market-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

  private fun samplePrice(coinId: String = "bitcoin", symbol: String = "BTC"): CoinPrice =
      CoinPrice(
          coinId = coinId,
          symbol = symbol,
          name = "Bitcoin",
          currentPriceUsd = 65000.0,
          marketCapUsd = 1_000_000.0,
          volume24hUsd = 500_000.0,
          priceChangePercent24h = 2.35,
          lastUpdated = Instant.parse("2024-01-01T00:00:00Z"))

  @Test
  fun `saveAll logs a warning when a row violates a column constraint`() = runBlocking {
    MarketRepository.initDatabase(testDbUrl(), "sa", "")

    val logger = LoggerFactory.getLogger("MarketRepository") as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(appender)

    val tooLongSymbol = "X".repeat(64) // column is varchar(16)
    MarketRepository.saveAll(listOf(samplePrice(symbol = tooLongSymbol)))

    logger.detachAppender(appender)
    assertTrue(
        appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("bitcoin") })
  }

  @Test
  fun `saveAll continues past a failing row on H2 (same-transaction isolation does not hold on Postgres)`() =
      runBlocking {
        MarketRepository.initDatabase(testDbUrl(), "sa", "")

        val tooLongSymbol = "X".repeat(64)
        MarketRepository.saveAll(
            listOf(
                samplePrice(coinId = "ethereum", symbol = tooLongSymbol),
                samplePrice(coinId = "bitcoin")))

        val count = transaction { MarketPriceTable.selectAll().count() }
        assertEquals(1, count)
      }
}
