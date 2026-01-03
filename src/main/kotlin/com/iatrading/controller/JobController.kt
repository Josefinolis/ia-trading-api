package com.iatrading.controller

import com.iatrading.dto.JobStatusResponse
import com.iatrading.dto.JobTriggerResponse
import com.iatrading.exception.JobAlreadyRunningException
import com.iatrading.scheduler.AnalyzerScheduler
import com.iatrading.scheduler.NewsFetcherScheduler
import com.iatrading.util.JobTracker
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs", description = "Background job management")
class JobController(
    private val jobTracker: JobTracker,
    private val newsFetcherScheduler: NewsFetcherScheduler,
    private val analyzerScheduler: AnalyzerScheduler
) {
    // Virtual thread executor for background tasks
    private val executor: Executor = Executors.newVirtualThreadPerTaskExecutor()

    @PostMapping("/fetch-news")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger news fetch for all tickers in watchlist")
    fun triggerFetchNews(): JobTriggerResponse {
        logger.info { "POST /api/jobs/fetch-news" }

        val jobId = "fetch_all_news"

        if (jobTracker.isJobRunning(jobId)) {
            throw JobAlreadyRunningException(jobId)
        }

        // Start background job
        CompletableFuture.runAsync({
            try {
                newsFetcherScheduler.fetchAllNews()
            } catch (e: Exception) {
                logger.error(e) { "News fetch job failed" }
            }
        }, executor)

        return JobTriggerResponse(
            message = "News fetch job started in background",
            jobId = jobId,
            status = "running"
        )
    }

    @PostMapping("/fetch-news/{symbol}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger news fetch for a specific ticker")
    fun triggerFetchNewsTicker(@PathVariable symbol: String): JobTriggerResponse {
        val ticker = symbol.uppercase()
        logger.info { "POST /api/jobs/fetch-news/$ticker" }

        val jobId = "fetch_news_ticker"

        // Start background job
        CompletableFuture.runAsync({
            try {
                newsFetcherScheduler.fetchNewsForTicker(ticker)
            } catch (e: Exception) {
                logger.error(e) { "News fetch for $ticker failed" }
            }
        }, executor)

        return JobTriggerResponse(
            message = "News fetch job for $ticker started in background",
            jobId = jobId,
            status = "running"
        )
    }

    @PostMapping("/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger sentiment analysis for all pending news")
    fun triggerAnalyze(): JobTriggerResponse {
        logger.info { "POST /api/jobs/analyze" }

        val jobId = "analyze_pending"

        if (jobTracker.isJobRunning(jobId)) {
            throw JobAlreadyRunningException(jobId)
        }

        // Start background job
        CompletableFuture.runAsync({
            try {
                analyzerScheduler.analyzeAllPending()
            } catch (e: Exception) {
                logger.error(e) { "Analysis job failed" }
            }
        }, executor)

        return JobTriggerResponse(
            message = "Analysis job started in background",
            jobId = jobId,
            status = "running"
        )
    }

    @GetMapping("/status")
    @Operation(summary = "Get status of all background jobs")
    fun getJobsStatus(): JobStatusResponse {
        logger.debug { "GET /api/jobs/status" }

        val allStatus = jobTracker.getAllStatus()

        @Suppress("UNCHECKED_CAST")
        val jobs = allStatus.mapValues { (_, value) ->
            com.iatrading.dto.JobInfo(
                status = value["status"] as? String ?: "unknown",
                lastRunTime = (value["lastRunTime"] as? String)?.let {
                    java.time.LocalDateTime.parse(it)
                },
                lastDuration = value["lastDuration"] as? Double,
                lastResult = value["lastResult"] as? Map<String, Any>,
                error = value["error"] as? String
            )
        }

        return JobStatusResponse(jobs = jobs)
    }
}
