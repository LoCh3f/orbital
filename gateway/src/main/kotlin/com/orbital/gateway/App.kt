package com.orbital.gateway

import com.orbital.plugins.configureMetrics
import com.orbital.plugins.configureMonitoring
import com.orbital.plugins.configureRequestTracing
import com.orbital.plugins.configureSerialization
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.response.respond
import java.net.URI
import kotlin.time.Duration.Companion.minutes
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisSentinelPool
import redis.clients.jedis.util.Pool

private const val REQUESTS_PER_MINUTE = 60
private const val RATE_LIMIT_WINDOW_SECONDS = 60

/** Starts the gateway's Netty server on port 8080. */
fun main() {
  embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
      .start(wait = true)
}

/**
 * Wires up serialization, health/metrics, request tracing, rate limiting, CORS, and the proxy
 * routes.
 */
fun Application.module() {
  configureSerialization()
  configureMonitoring("gateway")
  configureMetrics("gateway")
  configureRequestTracing()
  configureRateLimit(trustProxyHeaders = trustProxyHeadersFromEnv())
  configureCors()
  configureRouting()
}

/**
 * Permissive by default so any origin (including the web `orbital-app` build) can call this API
 * during local development. Set `CORS_ALLOWED_HOST` to restrict to a single HTTPS origin in
 * production deployments.
 */
private fun Application.configureCors() {
  install(CORS) {
    // Permissive by default (local dev, docker-compose — nothing sets this var there).
    // Set CORS_ALLOWED_HOST (e.g. "loch3f.github.io") to restrict to a specific origin.
    val allowedHost = System.getenv("CORS_ALLOWED_HOST")
    if (allowedHost.isNullOrBlank()) {
      anyHost()
    } else {
      allowHost(allowedHost, schemes = listOf("https"))
    }
    allowHeader(io.ktor.http.HttpHeaders.ContentType)
  }
}

/**
 * Global limit of [REQUESTS_PER_MINUTE] requests per minute, keyed by remote host. When
 * [trustProxyHeaders] is true, installs [XForwardedHeaders] first so `remoteHost` reflects the
 * original client's address instead of a reverse proxy's — only enable this (via
 * `TRUST_PROXY_HEADERS=true`) when the gateway sits behind infrastructure you control that
 * overwrites any client-supplied `X-Forwarded-For` header; otherwise a direct client could spoof it
 * to bypass rate limiting entirely.
 *
 * When [rateLimiter] is non-null (Redis/Sentinel configured — see [defaultRateLimiter]), it
 * replaces Ktor's built-in `RateLimit` plugin entirely: that plugin's token bucket is always
 * in-process, so with multiple gateway replicas behind `gateway-lb` it would enforce
 * `REQUESTS_PER_MINUTE` *per replica* rather than one true global limit. [rateLimiter] shares its
 * counters in Redis instead, so every replica enforces the same limit together.
 */
internal fun Application.configureRateLimit(
    trustProxyHeaders: Boolean,
    rateLimiter: RateLimiter? = defaultRateLimiter()
) {
  if (trustProxyHeaders) install(XForwardedHeaders)
  if (rateLimiter != null) {
    intercept(ApplicationCallPipeline.Plugins) {
      val key = call.request.origin.remoteHost
      if (!rateLimiter.tryAcquire(key)) {
        call.respond(HttpStatusCode.TooManyRequests)
        finish()
      }
    }
  } else {
    install(RateLimit) {
      global {
        rateLimiter(limit = REQUESTS_PER_MINUTE, refillPeriod = 1.minutes)
        requestKey { call -> call.request.origin.remoteHost }
      }
    }
  }
}

private fun trustProxyHeadersFromEnv(): Boolean =
    System.getenv("TRUST_PROXY_HEADERS")?.equals("true", ignoreCase = true) == true

/** Enforces a request limit for a given client [key], shared across however it's backed. */
internal interface RateLimiter {
  /** Returns true if the call is allowed, false if [key] has exceeded the limit for this window. */
  fun tryAcquire(key: String): Boolean
}

/** The handful of Redis operations [RedisRateLimiter] needs — narrowed for testability. */
internal interface RedisOps {
  /**
   * Atomically increments the counter at [key], setting it to expire after [windowSeconds] the
   * first time it's created, and returns the post-increment count.
   */
  fun incrWithExpire(key: String, windowSeconds: Int): Long
}

/**
 * [RedisOps] backed by a real Redis connection (plain or Sentinel-routed — [pool] is typed to the
 * common base, same reasoning as [RedisCache]). The increment-and-maybe-expire has to be one atomic
 * step (a Lua script via `EVAL`), not two round-trips, or concurrent requests from different
 * replicas could race: both could INCR to the same value before either sets the TTL, leaking the
 * key past its intended window.
 */
internal class JedisRedisOps(private val pool: Pool<Jedis>) : RedisOps {
  override fun incrWithExpire(key: String, windowSeconds: Int): Long =
      pool.resource.use { jedis ->
        jedis.eval(INCR_AND_EXPIRE_SCRIPT, listOf(key), listOf(windowSeconds.toString()))
      } as Long

  private companion object {
    const val INCR_AND_EXPIRE_SCRIPT =
        """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
          redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """
  }
}

/**
 * [RateLimiter] that enforces [limit] requests per [windowSeconds] by incrementing one shared Redis
 * counter per client key via [ops] — this is what makes the limit global across every gateway
 * replica instead of per-replica (see [configureRateLimit]'s doc comment).
 */
internal class RedisRateLimiter(
    private val ops: RedisOps,
    private val limit: Int,
    private val windowSeconds: Int
) : RateLimiter {
  override fun tryAcquire(key: String): Boolean =
      ops.incrWithExpire("ratelimit:$key", windowSeconds) <= limit
}

/**
 * Prefers a Sentinel-monitored Redis over a single `REDIS_URL` — same env vars and reasoning as
 * `Routing.kt`'s `defaultCache`. Returns null (falling back to Ktor's in-process limiter) when
 * neither is set, since a single gateway instance has no per-replica-vs-global problem to solve.
 */
private fun defaultRateLimiter(): RateLimiter? {
  sentinelConfigFromEnv()?.let { (masterName, sentinels) ->
    return RedisRateLimiter(
        JedisRedisOps(JedisSentinelPool(masterName, sentinels)),
        REQUESTS_PER_MINUTE,
        RATE_LIMIT_WINDOW_SECONDS)
  }
  val redisUrl = System.getenv("REDIS_URL")
  return redisUrl?.let {
    RedisRateLimiter(
        JedisRedisOps(JedisPool(URI(it))), REQUESTS_PER_MINUTE, RATE_LIMIT_WINDOW_SECONDS)
  }
}
