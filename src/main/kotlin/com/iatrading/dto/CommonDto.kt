package com.iatrading.dto

import java.time.LocalDateTime

/**
 * Health check response.
 */
data class HealthResponse(
    val status: String,
    val version: String,
    val database: String,
    val scheduler: String
)

/**
 * API service status.
 */
data class ApiServiceStatus(
    val available: Boolean,
    val cooldownUntil: String? = null,
    val message: String? = null
)

/**
 * API rate limit status response.
 */
data class ApiStatusResponse(
    val gemini: ApiServiceStatus,
    val alphaVantage: ApiServiceStatus
)

/**
 * Standard error response.
 */
data class ErrorResponse(
    val detail: String,
    val code: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Job trigger response.
 */
data class JobTriggerResponse(
    val message: String,
    val jobId: String,
    val status: String
)

/**
 * Job status response.
 */
data class JobStatusResponse(
    val jobs: Map<String, JobInfo>
)

/**
 * Individual job information.
 */
data class JobInfo(
    val status: String,
    val lastRunTime: LocalDateTime? = null,
    val lastDuration: Double? = null,
    val lastResult: Map<String, Any>? = null,
    val error: String? = null
)

/**
 * Scheduler status response.
 */
data class SchedulerStatusResponse(
    val running: Boolean,
    val jobs: List<ScheduledJobInfo>
)

/**
 * Scheduled job information.
 */
data class ScheduledJobInfo(
    val id: String,
    val name: String,
    val nextRun: String?
)
