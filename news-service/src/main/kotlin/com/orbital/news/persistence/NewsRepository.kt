@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "MagicNumber")

package com.orbital.news.persistence

import com.orbital.models.NewsArticle
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

object NewsTable : Table("news") {
  val id: Column<String> = varchar("id", 36)
  val title: Column<String> = varchar("title", 1024)
  val description: Column<String?> = varchar("description", 4096).nullable()
  val url: Column<String> = varchar("url", 2048)
  val sourceName: Column<String> = varchar("source_name", 256)
  val publishedAt: Column<Long> = long("published_at")
  val category: Column<String> = varchar("category", 64)
  override val primaryKey = PrimaryKey(id)
}

object NewsRepository {
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
              // ignore duplicate or constraint issues
            }
          }
        }
      }
}
