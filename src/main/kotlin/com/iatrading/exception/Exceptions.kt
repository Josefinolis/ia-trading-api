package com.iatrading.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Base exception for API errors.
 */
open class ApiException(
    message: String,
    val code: String? = null
) : RuntimeException(message)

/**
 * Resource not found exception.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
class NotFoundException(
    message: String,
    code: String? = "NOT_FOUND"
) : ApiException(message, code)

/**
 * Ticker not found exception.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
class TickerNotFoundException(ticker: String) : ApiException(
    message = "Ticker not found: $ticker",
    code = "TICKER_NOT_FOUND"
)

/**
 * Duplicate resource exception.
 */
@ResponseStatus(HttpStatus.CONFLICT)
class DuplicateException(
    message: String,
    code: String? = "DUPLICATE"
) : ApiException(message, code)

/**
 * External API error.
 */
class ExternalApiException(
    val service: String,
    message: String,
    cause: Throwable? = null
) : ApiException("$service API error: $message", "EXTERNAL_API_ERROR") {
    init {
        cause?.let { initCause(it) }
    }
}

/**
 * Rate limit exceeded exception.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
class RateLimitException(
    val service: String,
    val remainingSeconds: Int
) : ApiException(
    message = "$service API rate limit exceeded. Retry in $remainingSeconds seconds.",
    code = "RATE_LIMIT_EXCEEDED"
)

/**
 * Analysis error exception.
 */
class AnalysisException(
    message: String,
    cause: Throwable? = null
) : ApiException(message, "ANALYSIS_ERROR") {
    init {
        cause?.let { initCause(it) }
    }
}

/**
 * Job already running exception.
 */
@ResponseStatus(HttpStatus.CONFLICT)
class JobAlreadyRunningException(
    jobId: String
) : ApiException(
    message = "Job $jobId is already running. Check /api/jobs/status for progress.",
    code = "JOB_ALREADY_RUNNING"
)
