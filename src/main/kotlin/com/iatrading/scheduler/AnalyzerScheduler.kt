package com.iatrading.scheduler

import com.iatrading.client.GeminiClient
import com.iatrading.config.AppProperties
import com.iatrading.entity.NewsRecord
import com.iatrading.service.NewsService
import com.iatrading.service.SentimentService
import com.iatrading.util.JobTracker
import com.iatrading.util.RateLimitManager
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Scheduled job for analyzing pending news with Gemini AI.
 */
@Component
class AnalyzerScheduler(
    private val appProperties: AppProperties,
    private val newsService: NewsService,
    private val sentimentService: SentimentService,
    private val geminiClient: GeminiClient,
    private val rateLimitManager: RateLimitManager,
    private val jobTracker: JobTracker
) {
    /**
     * Analyze pending news items.
     * Runs every 5 minutes by default (configurable).
     */
    @Scheduled(fixedDelayString = "#{\${app.scheduler.analysis-interval-minutes:5} * 60000}")
    fun analyzePendingScheduled() {
        if (!appProperties.scheduler.enabled) {
            logger.debug { "Scheduler disabled, skipping analysis" }
            return
        }

        analyzePendingNews()
    }

    /**
     * Analyze pending news items (can be called manually).
     */
    fun analyzePendingNews(batchSize: Int = 10): Map<String, Any> {
        val jobId = "analyze_pending"
        val startTime = System.currentTimeMillis()

        logger.info { "Starting news analysis job..." }

        // Check if Gemini is available
        if (!rateLimitManager.gemini.isAvailable()) {
            val remaining = rateLimitManager.gemini.getRemainingCooldown()
            logger.info { "Gemini API in cooldown, ${remaining}s remaining. Skipping analysis." }
            return mapOf(
                "success" to false,
                "reason" to "gemini_cooldown",
                "remainingSeconds" to remaining
            )
        }

        // Check if already running
        if (jobTracker.isJobRunning(jobId)) {
            logger.warn { "Analysis job is already running, skipping" }
            return mapOf("success" to false, "reason" to "already_running")
        }

        jobTracker.startJob(jobId)

        try {
            val pendingNews = newsService.getPendingNews(batchSize)

            if (pendingNews.isEmpty()) {
                logger.info { "No pending news to analyze" }
                val duration = (System.currentTimeMillis() - startTime) / 1000.0
                jobTracker.completeJob(jobId, duration)
                return mapOf(
                    "success" to true,
                    "duration" to duration,
                    "successCount" to 0,
                    "errorCount" to 0,
                    "tickersUpdated" to 0
                )
            }

            logger.info { "Found ${pendingNews.size} pending news items to analyze" }

            val processedTickers = mutableSetOf<String>()
            var successCount = 0
            var errorCount = 0

            for ((index, news) in pendingNews.withIndex()) {
                try {
                    logger.info { "Processing ${index + 1}/${pendingNews.size}: ${news.title.take(50)}..." }

                    val analysis = geminiClient.analyzeSentiment(news.ticker, news.summary)

                    if (analysis != null) {
                        newsService.updateNewsAnalysis(
                            newsId = news.id,
                            sentiment = analysis.sentiment,
                            justification = analysis.justification
                        )
                        processedTickers.add(news.ticker)
                        successCount++
                        logger.info { "Successfully analyzed news ${news.id} (${news.ticker}): ${analysis.sentiment}" }
                    } else {
                        logger.warn { "No analysis returned for news ${news.id}" }
                        errorCount++
                    }

                } catch (e: Exception) {
                    logger.error(e) { "Error analyzing news ${news.id}" }
                    errorCount++
                }
            }

            // Update aggregated sentiment for affected tickers
            logger.info { "Updating sentiment aggregation for ${processedTickers.size} tickers..." }
            for (ticker in processedTickers) {
                try {
                    sentimentService.updateTickerSentiment(ticker)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update sentiment for $ticker" }
                }
            }

            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            logger.info {
                "Analysis job completed in ${duration}s. " +
                        "Success: $successCount, Errors: $errorCount, Tickers updated: ${processedTickers.size}"
            }

            jobTracker.completeJob(
                jobId, duration,
                mapOf(
                    "successCount" to successCount,
                    "errorCount" to errorCount,
                    "tickersUpdated" to processedTickers.size
                )
            )

            return mapOf(
                "success" to true,
                "duration" to duration,
                "successCount" to successCount,
                "errorCount" to errorCount,
                "tickersUpdated" to processedTickers.size
            )

        } catch (e: Exception) {
            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            logger.error(e) { "Analysis job failed" }
            jobTracker.completeJob(jobId, duration, error = e.message)
            return mapOf(
                "success" to false,
                "duration" to duration,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    /**
     * Analyze all pending news (no batch limit).
     */
    fun analyzeAllPending(): Map<String, Any> {
        return analyzePendingNews(batchSize = 1000)
    }

    /**
     * Analyze pending news for a specific ticker.
     */
    fun analyzePendingForTicker(tickerSymbol: String): Map<String, Any> {
        val ticker = tickerSymbol.uppercase()
        val startTime = System.currentTimeMillis()

        logger.info { "Analyzing pending news for $ticker..." }

        // Check if Gemini is available
        if (!rateLimitManager.gemini.isAvailable()) {
            val remaining = rateLimitManager.gemini.getRemainingCooldown()
            logger.info { "Gemini API in cooldown, ${remaining}s remaining. Skipping analysis." }
            return mapOf(
                "success" to false,
                "reason" to "gemini_cooldown",
                "remainingSeconds" to remaining
            )
        }

        try {
            val pendingNews = newsService.getPendingNewsForTicker(ticker)

            if (pendingNews.isEmpty()) {
                logger.info { "No pending news to analyze for $ticker" }
                val duration = (System.currentTimeMillis() - startTime) / 1000.0
                return mapOf(
                    "success" to true,
                    "duration" to duration,
                    "successCount" to 0,
                    "ticker" to ticker
                )
            }

            logger.info { "Found ${pendingNews.size} pending news items for $ticker" }

            var successCount = 0
            var errorCount = 0

            for ((index, news) in pendingNews.withIndex()) {
                try {
                    logger.info { "Processing ${index + 1}/${pendingNews.size}: ${news.title.take(50)}..." }

                    val analysis = geminiClient.analyzeSentiment(news.ticker, news.summary)

                    if (analysis != null) {
                        newsService.updateNewsAnalysis(
                            newsId = news.id,
                            sentiment = analysis.sentiment,
                            justification = analysis.justification
                        )
                        successCount++
                        logger.info { "Analyzed news ${news.id}: ${analysis.sentiment}" }
                    } else {
                        errorCount++
                    }

                } catch (e: Exception) {
                    logger.error(e) { "Error analyzing news ${news.id}" }
                    errorCount++
                }
            }

            // Update aggregated sentiment
            logger.info { "Updating sentiment aggregation for $ticker..." }
            try {
                sentimentService.updateTickerSentiment(ticker)
            } catch (e: Exception) {
                logger.error(e) { "Failed to update sentiment for $ticker" }
            }

            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            logger.info { "Analysis for $ticker completed in ${duration}s. Success: $successCount, Errors: $errorCount" }

            return mapOf(
                "success" to true,
                "duration" to duration,
                "successCount" to successCount,
                "errorCount" to errorCount,
                "ticker" to ticker
            )

        } catch (e: Exception) {
            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            logger.error(e) { "Analysis for $ticker failed" }
            return mapOf(
                "success" to false,
                "duration" to duration,
                "ticker" to ticker,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }
}
