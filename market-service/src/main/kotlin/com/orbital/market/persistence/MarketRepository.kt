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
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

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
}

object MarketRepository {
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
              // ignore duplicate or constraint issues
            }
          }
        }
      }
}
