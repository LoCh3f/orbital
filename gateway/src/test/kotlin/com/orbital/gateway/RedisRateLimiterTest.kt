package com.orbital.gateway

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedisRateLimiterTest {
  private class FakeRedisOps : RedisOps {
    private val counts = mutableMapOf<String, Long>()

    override fun incrWithExpire(key: String, windowSeconds: Int): Long =
        counts.merge(key, 1L, Long::plus) ?: 1L
  }

  @Test
  fun `allows calls up to the limit then rejects`() {
    val limiter = RedisRateLimiter(FakeRedisOps(), limit = 2, windowSeconds = 60)

    assertTrue(limiter.tryAcquire("client-a"))
    assertTrue(limiter.tryAcquire("client-a"))
    assertFalse(limiter.tryAcquire("client-a"))
  }

  @Test
  fun `tracks separate clients independently`() {
    val limiter = RedisRateLimiter(FakeRedisOps(), limit = 1, windowSeconds = 60)

    assertTrue(limiter.tryAcquire("client-a"))
    assertTrue(limiter.tryAcquire("client-b"))
    assertFalse(limiter.tryAcquire("client-a"))
    assertFalse(limiter.tryAcquire("client-b"))
  }
}
