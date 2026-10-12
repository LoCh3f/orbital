@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "MagicNumber")

package com.orbital.news.persistence

import com.orbital.models.NewsArticle
import com.orbital.models.NewsCategory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/** Caps how many stored articles [NewsRepository.getHistory] returns. */
private const val MAX_HISTORY_ARTICLES = 200

/**
 * Append-only log of fetched articles — [NewsMapper] assigns a fresh id per fetch, so repeated
 * fetches of the same underlying article accumulate distinct rows rather than upserting.
 */
object NewsTable : Table("news") {
  val id: Column<String> = varchar("id", 36)
  val title: Column<String> = varchar("title", 1024)
  val description: Column<String?> = varchar("description", 4096).nullable()
  val url: Column<String> = varchar("url", 2048)
  val sourceName: Column<String> = varchar("source_name", 256)
  val publishedAt: Column<Long> = long("published_at")
  val category: Column<String> = varchar("category", 64)
  override val primaryKey = PrimaryKey(id)

  init {
    // getHistory filters by category and orders by publishedAt — without this, that's a full
    // table scan on a table that only ever grows (append-only, one row per fetch per article).
    index(isUnique = false, columns = arrayOf(category, publishedAt))
  }
}

/** Exposed/HikariCP-backed persistence for [NewsTable]. */
object NewsRepository {
  private val logger = LoggerFactory.getLogger("NewsRepository")

  /** Connects to Postgres and creates [NewsTable] if it doesn't already exist. */
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
    transaction { SchemaUtils.createMissingTablesAndColumns(NewsTable) }
  }

  /**
   * Inserts one row per article inside a single transaction, catching and logging (never throwing)
   * any per-row failure. On H2 (used in tests), execution continues past a failed statement; on
   * Postgres, a failed insert aborts the whole surrounding transaction, so in production a single
   * bad row can take the rest of the batch down with it — the caller's own catch still prevents
   * this from failing the response, but rows are not independently committed.
   */
  suspend fun saveAll(articles: List<NewsArticle>) =
      withContext(Dispatchers.IO) {
        transaction {
          for (a in articles) {
            val id = a.id.toString()
            try {
              NewsTable.insert {
                it[NewsTable.id] = id
                it[title] = a.title
                it[description] = a.description
                it[url] = a.url
                it[sourceName] = a.sourceName
                it[publishedAt] = a.publishedAt.epochSecond
                it[category] = a.category.name
              }
            } catch (e: Exception) {
              logger.warn("Failed to persist article id='$id' sourceName='${a.sourceName}'", e)
            }
          }
        }
      }

  /**
   * Returns up to [MAX_HISTORY_ARTICLES] stored articles, most recent first — optionally filtered
   * to one [category], otherwise across all of them.
   */
  suspend fun getHistory(category: NewsCategory? = null): List<NewsArticle> =
      withContext(Dispatchers.IO) {
        transaction {
          val rows: Query =
              if (category != null) NewsTable.select { NewsTable.category eq category.name }
              else NewsTable.selectAll()
          rows
              .orderBy(NewsTable.publishedAt, SortOrder.DESC)
              .limit(MAX_HISTORY_ARTICLES)
              .map(::toArticle)
        }
      }

  private fun toArticle(row: ResultRow) =
      NewsArticle(
          id = UUID.fromString(row[NewsTable.id]),
          title = row[NewsTable.title],
          description = row[NewsTable.description].orEmpty(),
          url = row[NewsTable.url],
          sourceName = row[NewsTable.sourceName],
          publishedAt = Instant.ofEpochSecond(row[NewsTable.publishedAt]),
          category = NewsCategory.valueOf(row[NewsTable.category]))
}
