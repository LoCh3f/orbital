package com.orbital.market.persistence

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.orbital.models.CoinPrice
import java.sql.DriverManager
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class MarketRepositoryTest {
  private fun testDbUrl(): String {
    val postgres = System.getenv("TEST_POSTGRES_URL")
    if (postgres != null) {
      val schema = "review_" + System.nanoTime()
      DriverManager.getConnection(postgres).use { connection ->
        connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
      }
      return postgres + "&currentSchema=" + schema
    }
    return "jdbc:h2:mem:market-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
  }

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
    MarketRepository.initDatabase(
        testDbUrl(), System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")

    val logger = LoggerFactory.getLogger("MarketRepository") as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(appender)

    val tooLongSymbol = "X".repeat(64) // column is varchar(16)
    runCatching { MarketRepository.saveAll(listOf(samplePrice(symbol = tooLongSymbol))) }

    logger.detachAppender(appender)
    assertTrue(
        appender.list.any { it.level == Level.WARN && it.formattedMessage.contains("bitcoin") })
  }

  @Test
  fun `saveAll rolls back the entire invalid batch`() = runBlocking {
    MarketRepository.initDatabase(
        testDbUrl(), System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")

    val tooLongSymbol = "X".repeat(64)
    val failed = runCatching {
      MarketRepository.saveAll(
          listOf(
              samplePrice(coinId = "ethereum", symbol = tooLongSymbol),
              samplePrice(coinId = "bitcoin")))
    }
    assertTrue(failed.isFailure)

    val count = transaction { MarketPriceTable.selectAll().count() }
    assertEquals(0, count)
  }

  @Test
  fun `getPriceHistory returns a coin's snapshots oldest first, excluding other coins`() =
      runBlocking {
        MarketRepository.initDatabase(
            testDbUrl(),
            System.getenv("TEST_DB_USER") ?: "sa",
            System.getenv("TEST_DB_PASSWORD") ?: "")

        MarketRepository.saveAll(
            listOf(
                samplePrice(coinId = "bitcoin")
                    .copy(
                        currentPriceUsd = 10.0,
                        lastUpdated = Instant.parse("2024-01-01T00:00:00Z")),
                samplePrice(coinId = "ethereum").copy(currentPriceUsd = 999.0),
                samplePrice(coinId = "bitcoin")
                    .copy(
                        currentPriceUsd = 20.0,
                        lastUpdated = Instant.parse("2024-01-02T00:00:00Z"))))

        val history = MarketRepository.getPriceHistory("bitcoin")

        assertEquals(listOf(10.0, 20.0), history)
      }

  @Test
  fun `hasHistory is false until a snapshot is stored for that coin`() = runBlocking {
    MarketRepository.initDatabase(
        testDbUrl(), System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")

    assertEquals(false, MarketRepository.hasHistory("bitcoin"))

    MarketRepository.saveAll(listOf(samplePrice(coinId = "bitcoin")))

    assertTrue(MarketRepository.hasHistory("bitcoin"))
    assertEquals(false, MarketRepository.hasHistory("ethereum"))
  }

  /**
   * Regression test for the audit question of whether `(coin_id, last_updated)` actually gets added
   * to a database that already had `market_prices` *without* that index — i.e. an already running
   * deployment being upgraded to this code, not a fresh one. `createMissingTablesAndColumns` is not
   * guaranteed across every Exposed version to retroactively add indexes to existing tables, so
   * this is verified directly against the pinned version (0.41.1) rather than assumed.
   */
  @Test
  @Suppress("CyclomaticComplexMethod")
  fun `initDatabase adds the missing index to a table that predates it`() = runBlocking {
    val url = testDbUrl()

    // Create the table by hand, matching the pre-index schema, before Exposed ever touches it.
    DriverManager.getConnection(
            url, System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")
        .use { connection ->
          connection.createStatement().use { statement ->
            statement.execute(
                """
            CREATE TABLE market_prices (
              id VARCHAR(36) PRIMARY KEY,
              coin_id VARCHAR(64) NOT NULL,
              symbol VARCHAR(16) NOT NULL,
              name VARCHAR(128) NOT NULL,
              current_price_usd DOUBLE PRECISION NOT NULL,
              market_cap_usd DOUBLE PRECISION NOT NULL,
              volume_24h_usd DOUBLE PRECISION NOT NULL,
              price_change_percent_24h DOUBLE PRECISION NOT NULL,
              last_updated BIGINT NOT NULL
            )
            """
                    .trimIndent())
          }
        }

    MarketRepository.initDatabase(
        url, System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")

    val indexFound =
        DriverManager.getConnection(
                url, System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")
            .use { connection ->
              connection.metaData
                  .getIndexInfo(
                      null,
                      null,
                      if (url.startsWith("jdbc:postgresql:")) "market_prices" else "MARKET_PRICES",
                      false,
                      false)
                  .use { rs ->
                    val columnsByIndex = mutableMapOf<String, MutableSet<String>>()
                    while (rs.next()) {
                      val indexName = rs.getString("INDEX_NAME")
                      val columnName = rs.getString("COLUMN_NAME")
                      if (indexName != null && columnName != null) {
                        columnsByIndex
                            .getOrPut(indexName) { mutableSetOf() }
                            .add(columnName.uppercase())
                      }
                    }
                    columnsByIndex.values.any { it.containsAll(setOf("COIN_ID", "LAST_UPDATED")) }
                  }
            }

    assertTrue(
        indexFound,
        "Exposed's createMissingTablesAndColumns did not add the (coin_id, last_updated) index " +
            "to a table that predates it — an already-running deployment needs an explicit " +
            "migration, this doesn't happen automatically on upgrade.")
  }

  @Test
  fun `live observations do not complete backfill and timestamps survive`() = runBlocking {
    MarketRepository.initDatabase(
        testDbUrl(), System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")
    MarketRepository.saveAll(listOf(samplePrice()))
    assertEquals(false, MarketRepository.backfillComplete("bitcoin"))
    val points = MarketRepository.getTimestampedHistory("bitcoin")
    assertEquals(Instant.parse("2024-01-01T00:00:00Z").epochSecond, points.single().timestamp)
    MarketRepository.markBackfillComplete("bitcoin")
    assertTrue(MarketRepository.backfillComplete("bitcoin"))
  }
}
