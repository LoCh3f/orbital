@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package com.orbital.market.history

import com.orbital.market.api.CoinGeckoClient
import com.orbital.market.api.CoinGeckoMapper
import com.orbital.market.persistence.MarketRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** Resumable, bounded imports. HTTP request handlers enqueue work, never download archives. */
class HistoryWorker(
    private val coins: CoinGeckoClient,
    private val archive: BinanceArchive = BinanceArchive()
) {
  private val logger = LoggerFactory.getLogger("HistoryWorker")

  suspend fun run() {
    while (currentCoroutineContext().isActive) {
      val job = nextJobSafely()
      if (job == null) {
        delay(2000)
        continue
      }
      try {
        if (job.source == "binance") importBinance(job) else importCoinGecko(job)
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        logger.warn(
            "History import delayed for coin={} source={}: {}",
            job.coinId,
            job.source,
            error.javaClass.simpleName)
        retrySafely(job)
        delay(4000)
      }
    }
  }

  private suspend fun retrySafely(job: ArchiveJob) {
    try {
      withContext(Dispatchers.IO) { HistoryQueue.retry(job) }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failure: Exception) {
      // The durable job remains pending when the database cannot record its retry delay.
      logger.warn("History retry recording delayed: {}", failure.javaClass.simpleName)
    }
  }

  private suspend fun nextJobSafely(): ArchiveJob? =
      try {
        withContext(Dispatchers.IO) { HistoryQueue.nextJob() }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        logger.warn("History queue temporarily unavailable: {}", failure.javaClass.simpleName)
        delay(4000)
        null
      }

  private suspend fun importBinance(job: ArchiveJob) {
    val pair = withContext(Dispatchers.IO) { HistoryRepository.pair(job.coinId) } ?: return
    val interval =
        when (job.step) {
          300L -> "5m"
          3600L -> "1h"
          else -> "1d"
        }
    val pending =
        withContext(Dispatchers.IO) {
          archive.files(pair, interval, job.from, job.to).filterNot {
            HistoryRepository.imported(it)
          }
        }
    for (key in pending.take(2)) {
      withContext(Dispatchers.IO) {
        val (checksum, points) = archive.download(key)
        HistoryRepository.saveArchive(job.coinId, pair, job.step, key, checksum, points)
      }
      delay(1000)
    }
    withContext(Dispatchers.IO) { HistoryQueue.finish(job, pending.size <= 2) }
  }

  private suspend fun importCoinGecko(job: ArchiveJob) {
    val now = Instant.now().epochSecond
    // The public plan cannot supply finer data outside its permitted historical window.
    val days = if (job.step < 3600) "1" else if (job.step < 86400) "90" else "365"
    if (job.to >= now - 31536000) {
      val chart = coins.getMarketChart(job.coinId, days)
      val (symbol, name) = withContext(Dispatchers.IO) { HistoryRepository.metadata(job.coinId) }
      val snapshots = CoinGeckoMapper.toHistorySnapshots(job.coinId, symbol, name, chart)
      MarketRepository.saveAll(
          snapshots, if (days == "1") 300L else if (days == "90") 3600L else 86400L)
    }
    withContext(Dispatchers.IO) { HistoryQueue.finish(job, true) }
    delay(4000)
  }
}
