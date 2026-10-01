package com.orbital.core

/**
 * Base type for domain-level failures that route handlers translate into an [ApiResponse.Error]
 * with a specific HTTP status — see `respondWithError` in each service's routing plugin.
 */
sealed class OrbitalException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** An upstream API (CoinGecko, NewsAPI) failed or was unreachable. Maps to `502 Bad Gateway`. */
class ExternalApiException(message: String, cause: Throwable? = null) :
    OrbitalException(message, cause)

/** The requested resource doesn't exist. Maps to `404 Not Found`. */
class NotFoundException(message: String, cause: Throwable? = null) :
    OrbitalException(message, cause)

/** An upstream or local rate limit was exceeded. */
class RateLimitException(message: String, cause: Throwable? = null) :
    OrbitalException(message, cause)
