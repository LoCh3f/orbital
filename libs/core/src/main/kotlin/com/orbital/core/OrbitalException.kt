package com.orbital.core

sealed class OrbitalException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class ExternalApiException(message: String, cause: Throwable? = null) :
    OrbitalException(message, cause)

class NotFoundException(message: String, cause: Throwable? = null) :
    OrbitalException(message, cause)

class RateLimitException(message: String, cause: Throwable? = null) :
    OrbitalException(message, cause)
