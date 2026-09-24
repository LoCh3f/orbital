package com.orbital.market.api

import com.orbital.core.NotFoundException
import com.orbital.models.CoinPrice
import com.orbital.models.MarketStats
import java.time.Instant

/** Translates CoinGecko's wire format ([CoinGeckoCryptoData]) into this service's domain types. */
object CoinGeckoMapper {
  /** Maps to [CoinPrice], defaulting a missing 24h change to `0.0`. */
  fun toCoinPrice(data: CoinGeckoCryptoData): CoinPrice =
      CoinPrice(
          coinId = data.id,
          symbol = data.symbol.uppercase(),
          name = data.name,
          currentPriceUsd = data.currentPrice,
          marketCapUsd = data.marketCap.toDouble(),
          volume24hUsd = data.totalVolume.toDouble(),
          priceChangePercent24h = data.priceChangePercentage24h ?: 0.0,
          lastUpdated = Instant.parse(data.lastUpdated))

  /**
   * Maps to [MarketStats]. `sparkline7d` is always empty — CoinGecko sparkline data isn't fetched.
   */
  fun toMarketStats(data: CoinGeckoCryptoData): MarketStats =
      MarketStats(
          coinId = data.id,
          ath = data.ath ?: 0.0,
          atl = data.atl ?: 0.0,
          circulatingSupply = data.circulatingSupply ?: 0.0,
          totalSupply = data.totalSupply ?: 0.0,
          sparkline7d = emptyList())

  /** Unwraps a nullable CoinGecko lookup, throwing [NotFoundException] for `null`. */
  fun requireFound(data: CoinGeckoCryptoData?, coinId: String): CoinGeckoCryptoData =
      data ?: throw NotFoundException("Coin '$coinId' was not found")
}
