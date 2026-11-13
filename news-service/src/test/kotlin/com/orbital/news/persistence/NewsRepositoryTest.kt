package com.orbital.news.persistence

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.orbital.models.NewsArticle
import com.orbital.models.NewsCategory
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class NewsRepositoryTest {
  private fun testDbUrl(): String {
    val postgres = System.getenv("TEST_POSTGRES_URL")
    if (postgres != null) {
      val schema = "review_" + System.nanoTime()
      DriverManager.getConnection(postgres).use { connection ->
        connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
      }
      return postgres + "&currentSchema=" + schema
    }
    return "jdbc:h2:mem:news-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
  }

  private fun sampleArticle(title: String = "A normal headline"): NewsArticle =
      NewsArticle(
          id = UUID.randomUUID(),
          title = title,
          description = "A description",
          url = "https://example.com/a",
          sourceName = "Example",
          publishedAt = Instant.parse("2024-01-01T00:00:00Z"),
          category = NewsCategory.CRYPTO)

  @Test
  fun `saveAll logs a warning when a row violates a column constraint`() = runBlocking {
    NewsRepository.initDatabase(
        testDbUrl(), System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")

    val logger = LoggerFactory.getLogger("NewsRepository") as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(appender)

    val tooLongSourceName = "X".repeat(300) // column is varchar(256)
    runCatching {
      NewsRepository.saveAll(listOf(sampleArticle().copy(sourceName = tooLongSourceName)))
    }

    logger.detachAppender(appender)
    assertTrue(appender.list.any { it.level == Level.WARN })
  }

  @Test
  fun `saveAll rolls back the entire invalid batch`() = runBlocking {
    NewsRepository.initDatabase(
        testDbUrl(), System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")

    val tooLongSourceName = "X".repeat(300)
    val failed = runCatching {
      NewsRepository.saveAll(
          listOf(sampleArticle().copy(sourceName = tooLongSourceName), sampleArticle()))
    }
    assertTrue(failed.isFailure)

    val count = transaction { NewsTable.selectAll().count() }
    assertEquals(0, count)
  }

  @Test
  fun `getHistory returns most recent first, filtered by category when given`() = runBlocking {
    NewsRepository.initDatabase(
        testDbUrl(), System.getenv("TEST_DB_USER") ?: "sa", System.getenv("TEST_DB_PASSWORD") ?: "")

    NewsRepository.saveAll(
        listOf(
            sampleArticle("Older crypto headline")
                .copy(publishedAt = Instant.parse("2024-01-01T00:00:00Z")),
            sampleArticle("Newer crypto headline")
                .copy(publishedAt = Instant.parse("2024-01-02T00:00:00Z")),
            sampleArticle("A tech headline").copy(category = NewsCategory.TECH)))

    val cryptoHistory = NewsRepository.getHistory(NewsCategory.CRYPTO)
    assertEquals(
        listOf("Newer crypto headline", "Older crypto headline"), cryptoHistory.map { it.title })

    val allHistory = NewsRepository.getHistory()
    assertEquals(3, allHistory.size)
  }
}
