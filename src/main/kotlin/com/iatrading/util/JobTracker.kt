package com.iatrading.util

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * Status of a background job.
 */
enum class JobStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * Information about a job's state.
 */
data class JobState(
    var status: JobStatus = JobStatus.IDLE,
    var lastRunTime: LocalDateTime? = null,
    var lastDuration: Double? = null,
    var lastResult: Map<String, Any>? = null,
    var error: String? = null
)

/**
 * Tracks the state of background jobs.
 */
@Component
class JobTracker {

    private val jobs = ConcurrentHashMap<String, JobState>()
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    private fun getLock(jobId: String): ReentrantLock =
        locks.computeIfAbsent(jobId) { ReentrantLock() }

    private fun getOrCreateJob(jobId: String): JobState =
        jobs.computeIfAbsent(jobId) { JobState() }

    /**
     * Check if a job is currently running.
     */
    fun isJobRunning(jobId: String): Boolean {
        return jobs[jobId]?.status == JobStatus.RUNNING
    }

    /**
     * Mark a job as started.
     */
    fun startJob(jobId: String) {
        getLock(jobId).withLock {
            val job = getOrCreateJob(jobId)
            job.status = JobStatus.RUNNING
            job.lastRunTime = LocalDateTime.now()
            job.error = null
            logger.info { "Job $jobId started" }
        }
    }

    /**
     * Mark a job as completed.
     */
    fun completeJob(
        jobId: String,
        duration: Double,
        result: Map<String, Any>? = null,
        error: String? = null
    ) {
        getLock(jobId).withLock {
            val job = getOrCreateJob(jobId)
            job.status = if (error != null) JobStatus.FAILED else JobStatus.COMPLETED
            job.lastDuration = duration
            job.lastResult = result
            job.error = error
            if (error != null) {
                logger.error { "Job $jobId failed after ${duration}s: $error" }
            } else {
                logger.info { "Job $jobId completed in ${duration}s" }
            }
        }
    }

    /**
     * Get status of a specific job.
     */
    fun getJobStatus(jobId: String): JobState? = jobs[jobId]

    /**
     * Get status of all jobs.
     */
    fun getAllStatus(): Map<String, Map<String, Any?>> {
        return jobs.mapValues { (_, state) ->
            mapOf(
                "status" to state.status.name.lowercase(),
                "lastRunTime" to state.lastRunTime?.toString(),
                "lastDuration" to state.lastDuration,
                "lastResult" to state.lastResult,
                "error" to state.error
            )
        }
    }
}
