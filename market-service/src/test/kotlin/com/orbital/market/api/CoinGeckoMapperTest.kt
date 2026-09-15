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
          lastUpdated = "2024-01-01T00:00:00Z")

  @Test
  fun `toCoinPrice maps and uppercases symbol`() {
    val price = CoinGeckoMapper.toCoinPrice(sample)
    assertEquals("bitcoin", price.coinId)
    assertEquals("BTC", price.symbol)
    assertEquals(65000.0, price.currentPriceUsd)
    assertEquals(2.35, price.priceChangePercent24h)
    assertEquals(Instant.parse("2024-01-01T00:00:00Z"), price.lastUpdated)
  }

  @Test
  fun `toCoinPrice defaults missing price change to zero`() {
    val price = CoinGeckoMapper.toCoinPrice(sample.copy(priceChangePercentage24h = null))
    assertEquals(0.0, price.priceChangePercent24h)
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
}
