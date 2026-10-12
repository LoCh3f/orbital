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
          lastUpdated = Instant.parse(data.lastUpdated),
          sparklineIn7d = data.sparklineIn7d?.price.orEmpty(),
          logoUrl = data.image)

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

  /**
   * Maps a [CoinGeckoMarketChart] into one [CoinPrice] snapshot per historical price point, paired
   * by index with the market-cap/volume sampled at the same timestamp. [symbol]/[name] come from
   * the coin's current metadata since `market_chart` carries no identity fields, and
   * `priceChangePercent24h` is always `0.0` — CoinGecko doesn't return a historical 24h-change
   * series, only the 24h snapshot used by [toCoinPrice].
   */
  fun toHistorySnapshots(
      coinId: String,
      symbol: String,
      name: String,
      chart: CoinGeckoMarketChart
  ): List<CoinPrice> =
      chart.prices.mapIndexedNotNull { index, point ->
        val timestampMs = point.getOrNull(0) ?: return@mapIndexedNotNull null
        val price = point.getOrNull(1) ?: return@mapIndexedNotNull null
        CoinPrice(
            coinId = coinId,
            symbol = symbol.uppercase(),
            name = name,
            currentPriceUsd = price,
            marketCapUsd = chart.marketCaps.getOrNull(index)?.getOrNull(1) ?: 0.0,
            volume24hUsd = chart.totalVolumes.getOrNull(index)?.getOrNull(1) ?: 0.0,
            priceChangePercent24h = 0.0,
            lastUpdated = Instant.ofEpochMilli(timestampMs.toLong()))
      }
}
