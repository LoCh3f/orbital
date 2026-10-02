package com.orbital.news.persistence

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.orbital.models.NewsArticle
import com.orbital.models.NewsCategory
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
  private fun testDbUrl() =
      "jdbc:h2:mem:news-${System.nanoTime()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

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
    NewsRepository.initDatabase(testDbUrl(), "sa", "")

    val logger = LoggerFactory.getLogger("NewsRepository") as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(appender)

    val tooLongSourceName = "X".repeat(300) // column is varchar(256)
    NewsRepository.saveAll(listOf(sampleArticle().copy(sourceName = tooLongSourceName)))

    logger.detachAppender(appender)
    assertTrue(appender.list.any { it.level == Level.WARN })
  }

  @Test
  fun `saveAll continues past a failing row on H2 (same-transaction isolation does not hold on Postgres)`() =
      runBlocking {
        NewsRepository.initDatabase(testDbUrl(), "sa", "")

        val tooLongSourceName = "X".repeat(300)
        NewsRepository.saveAll(
            listOf(sampleArticle().copy(sourceName = tooLongSourceName), sampleArticle()))

        val count = transaction { NewsTable.selectAll().count() }
        assertEquals(1, count)
      }
}
