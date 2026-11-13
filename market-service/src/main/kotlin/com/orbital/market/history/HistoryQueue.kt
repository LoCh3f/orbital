@file:Suppress("MagicNumber", "LongParameterList")

package com.orbital.market.history

import java.time.Instant
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object HistoryQueue {
  fun enqueue(coin: String, source: String, from: Long, to: Long, step: Long, priority: Int = 1) =
      transaction {
        // Normalized month bounds make repeated zoom/refresh requests share one durable job.
        val start =
            Instant.ofEpochSecond(from)
                .atZone(java.time.ZoneOffset.UTC)
                .withDayOfMonth(1)
                .toLocalDate()
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toEpochSecond()
        val end =
            Instant.ofEpochSecond(to)
                .atZone(java.time.ZoneOffset.UTC)
                .withDayOfMonth(1)
                .toLocalDate()
                .plusMonths(1)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toEpochSecond() - 1
        val id = historyDigest("$coin:$source:$start:$end:$step")
        val existing = HistoryJobs.select { HistoryJobs.id eq id }.firstOrNull()
        val now = Instant.now().epochSecond
        if (existing == null)
            HistoryJobs.insert {
              it[HistoryJobs.id] = id
              it[coinId] = coin
              it[HistoryJobs.provider] = source
              it[HistoryJobs.from] = start
              it[HistoryJobs.to] = end
              it[HistoryJobs.step] = step
              it[HistoryJobs.priority] = priority
              it[state] = "pending"
              it[updated] = now
            }
        else if (existing[HistoryJobs.state] != "complete" ||
            now - existing[HistoryJobs.updated] > 21600) {
          HistoryJobs.update({ HistoryJobs.id eq id }) {
            it[state] = "pending"
            it[HistoryJobs.priority] = maxOf(priority, existing[HistoryJobs.priority])
          }
        }
      }

  fun nextJob(): ArchiveJob? = transaction {
    val active = HistoryCoins.select { HistoryCoins.active eq true }.map { it[HistoryCoins.coinId] }
    if (active.isEmpty()) return@transaction null
    HistoryJobs.select {
          (HistoryJobs.state neq "complete") and
              (HistoryJobs.coinId inList active) and
              (HistoryJobs.updated lessEq Instant.now().epochSecond)
        }
        .orderBy(HistoryJobs.priority to SortOrder.DESC, HistoryJobs.updated to SortOrder.ASC)
        .limit(1)
        .firstOrNull()
        ?.let {
          ArchiveJob(
              it[HistoryJobs.id],
              it[HistoryJobs.coinId],
              it[HistoryJobs.provider],
              it[HistoryJobs.from],
              it[HistoryJobs.to],
              it[HistoryJobs.step])
        }
  }

  // Failed providers yield to other tracked coins instead of monopolizing the queue.
  fun retry(job: ArchiveJob) = transaction {
    HistoryJobs.update({ HistoryJobs.id eq job.id }) {
      it[state] = "pending"
      it[updated] = Instant.now().epochSecond + 60
    }
  }

  fun finish(job: ArchiveJob, complete: Boolean) = transaction {
    HistoryJobs.update({ HistoryJobs.id eq job.id }) {
      it[state] = if (complete) "complete" else "pending"
      it[updated] = Instant.now().epochSecond
    }
  }
}
