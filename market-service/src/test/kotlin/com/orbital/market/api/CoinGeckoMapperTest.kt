package com.orbital.market.api

import com.orbital.core.NotFoundException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoinGeckoMapperTest {
  private val sample =
      CoinGeckoCryptoData(
          id = "bitcoin",
          symbol = "btc",
          name = "Bitcoin",
          image = "https://example.com/btc.png",
          currentPrice = 65000.0,
          marketCap = 1_280_000_000_000L,
          marketCapRank = 1,
          totalVolume = 24_000_000_000L,
          priceChangePercentage24h = 2.35,
          ath = 69000.0,
          atl = 67.81,
          circulatingSupply = 19_600_000.0,
          totalSupply = 21_000_000.0,
          lastUpdated = "2024-01-01T00:00:00Z",
          sparklineIn7d = CoinGeckoSparkline(price = listOf(64000.0, 64500.0, 65000.0)))

  @Test
  fun `toCoinPrice maps and uppercases symbol`() {
    val price = CoinGeckoMapper.toCoinPrice(sample)
    assertEquals("bitcoin", price.coinId)
    assertEquals("BTC", price.symbol)
    assertEquals(65000.0, price.currentPriceUsd)
    assertEquals(2.35, price.priceChangePercent24h)
    assertEquals(Instant.parse("2024-01-01T00:00:00Z"), price.lastUpdated)
    assertEquals(listOf(64000.0, 64500.0, 65000.0), price.sparklineIn7d)
    assertEquals("https://example.com/btc.png", price.logoUrl)
  }

  @Test
  fun `toCoinPrice defaults missing price change to zero`() {
    val price = CoinGeckoMapper.toCoinPrice(sample.copy(priceChangePercentage24h = null))
    assertEquals(0.0, price.priceChangePercent24h)
  }

  @Test
  fun `toCoinPrice defaults missing sparkline to empty list`() {
    val price = CoinGeckoMapper.toCoinPrice(sample.copy(sparklineIn7d = null))
    assertEquals(emptyList(), price.sparklineIn7d)
  }

  @Test
  fun `toMarketStats maps ath, atl and supply`() {
    val stats = CoinGeckoMapper.toMarketStats(sample)
    assertEquals("bitcoin", stats.coinId)
    assertEquals(69000.0, stats.ath)
    assertEquals(67.81, stats.atl)
    assertEquals(19_600_000.0, stats.circulatingSupply)
    assertEquals(21_000_000.0, stats.totalSupply)
  }

  @Test
  fun `requireFound returns data when present`() {
    assertEquals(sample, CoinGeckoMapper.requireFound(sample, "bitcoin"))
  }

  @Test
  fun `requireFound throws NotFoundException when null`() {
    assertFailsWith<NotFoundException> { CoinGeckoMapper.requireFound(null, "bitcoin") }
  }

  @Test
  fun `toHistorySnapshots pairs each price with the market cap and volume at the same index`() {
    val chart =
        CoinGeckoMarketChart(
            prices = listOf(listOf(1_000.0, 100.0), listOf(2_000.0, 110.0)),
            marketCaps = listOf(listOf(1_000.0, 1_900_000.0), listOf(2_000.0, 2_090_000.0)),
            totalVolumes = listOf(listOf(1_000.0, 500.0), listOf(2_000.0, 550.0)))

    val snapshots = CoinGeckoMapper.toHistorySnapshots("bitcoin", "btc", "Bitcoin", chart)

    assertEquals(2, snapshots.size)
    assertEquals("bitcoin", snapshots[0].coinId)
    assertEquals("BTC", snapshots[0].symbol)
    assertEquals(100.0, snapshots[0].currentPriceUsd)
    assertEquals(1_900_000.0, snapshots[0].marketCapUsd)
    assertEquals(500.0, snapshots[0].volume24hUsd)
    assertEquals(0.0, snapshots[0].priceChangePercent24h)
    assertEquals(Instant.ofEpochMilli(1_000), snapshots[0].lastUpdated)
    assertEquals(110.0, snapshots[1].currentPriceUsd)
  }

  @Test
  fun `toHistorySnapshots defaults missing market cap or volume to zero`() {
    val chart = CoinGeckoMarketChart(prices = listOf(listOf(1_000.0, 100.0)))

    val snapshots = CoinGeckoMapper.toHistorySnapshots("bitcoin", "btc", "Bitcoin", chart)

    assertEquals(1, snapshots.size)
    assertEquals(0.0, snapshots[0].marketCapUsd)
    assertEquals(0.0, snapshots[0].volume24hUsd)
  }
}
