package com.iatrading.scheduler

import com.iatrading.client.NewsAggregator
import com.iatrading.config.AppProperties
import com.iatrading.service.NewsService
import com.iatrading.service.WatchlistService
import com.iatrading.util.JobTracker
import com.iatrading.util.RateLimitManager
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * Scheduled job for fetching news for all tickers.
 */
@Component
class NewsFetcherScheduler(
    private val appProperties: AppProperties,
    private val watchlistService: WatchlistService,
    private val newsService: NewsService,
    private val newsAggregator: NewsAggregator,
    private val rateLimitManager: RateLimitManager,
    private val jobTracker: JobTracker
) {
    /**
     * Fetch news for all active tickers.
     * Runs every 30 minutes by default (configurable).
     */
    @Scheduled(fixedDelayString = "\${app.scheduler.news-fetch-interval-minutes:30}000")
    fun fetchAllNewsScheduled() {
        if (!appProperties.scheduler.enabled) {
            logger.debug { "Scheduler disabled, skipping news fetch" }
            return
        }

        fetchAllNews()
    }

    /**
     * Fetch news for all active tickers (can be called manually).
     */
    fun fetchAllNews(): Map<String, Any> {
        val jobId = "fetch_all_news"
        val startTime = System.currentTimeMillis()

        logger.info { "Starting news fetch job..." }

        // Check if Alpha Vantage is available
        if (!rateLimitManager.alphaVantage.isAvailable()) {
            val remaining = rateLimitManager.alphaVantage.getRemainingCooldown()
            logger.info { "Alpha Vantage API in cooldown, ${remaining}s remaining. Skipping fetch." }
            return mapOf(
                "success" to false,
                "reason" to "alpha_vantage_cooldown",
                "remainingSeconds" to remaining
            )
        }

        // Check if already running
        if (jobTracker.isJobRunning(jobId)) {
            logger.warn { "News fetch job is already running, skipping" }
            return mapOf("success" to false, "reason" to "already_running")
        }

        jobTracker.startJob(jobId)

        try {
            val tickers = watchlistService.getAllActiveTickerEntities()

            if (tickers.isEmpty()) {
                logger.info { "No active tickers in watchlist, skipping fetch" }
                val duration = (System.currentTimeMillis() - startTime) / 1000.0
                jobTracker.completeJob(jobId, duration)
                return mapOf(
                    "success" to true,
                    "duration" to duration,
                    "totalSaved" to 0,
                    "tickersProcessed" to 0
                )
            }

            logger.info { "Found ${tickers.size} active tickers to process" }

            val timeTo = LocalDateTime.now()
            val timeFrom = timeTo.minusHours(6)

            var totalSaved = 0
            var errorCount = 0

            for ((index, ticker) in tickers.withIndex()) {
                try {
                    logger.info { "Processing ${index + 1}/${tickers.size}: Fetching news for ${ticker.ticker}" }

                    val newsItems = newsAggregator.fetchAll(
                        ticker = ticker.ticker,
                        timeFrom = timeFrom,
                        timeTo = timeTo
                    )

                    if (newsItems.isNotEmpty()) {
                        val saved = newsService.saveNewsItems(ticker.ticker, newsItems)
                        totalSaved += saved
                        logger.info { "Saved $saved new items for ${ticker.ticker} (found ${newsItems.size} total)" }
                    } else {
                        logger.info { "No news found for ${ticker.ticker}" }
                    }

                } catch (e: Exception) {
                    logger.error(e) { "Failed to fetch news for ${ticker.ticker}" }
                    errorCount++
                }
            }

            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            logger.info {
                "News fetch job completed in ${duration}s. " +
                        "Total saved: $totalSaved, Tickers processed: ${tickers.size}, Errors: $errorCount"
            }

            jobTracker.completeJob(
                jobId, duration,
                mapOf(
                    "totalSaved" to totalSaved,
                    "tickersProcessed" to tickers.size,
                    "errorCount" to errorCount
                )
            )

            return mapOf(
                "success" to true,
                "duration" to duration,
                "totalSaved" to totalSaved,
                "tickersProcessed" to tickers.size,
                "errorCount" to errorCount
            )

        } catch (e: Exception) {
            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            logger.error(e) { "News fetch job failed" }
            jobTracker.completeJob(jobId, duration, error = e.message)
            return mapOf(
                "success" to false,
                "duration" to duration,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    /**
     * Fetch news for a specific ticker.
     */
    fun fetchNewsForTicker(tickerSymbol: String, hours: Int = 24): Map<String, Any> {
        val ticker = tickerSymbol.uppercase()
        val startTime = System.currentTimeMillis()

        logger.info { "Starting news fetch for $ticker (last $hours hours)" }

        val timeTo = LocalDateTime.now()
        val timeFrom = timeTo.minusHours(hours.toLong())

        try {
            val newsItems = newsAggregator.fetchAll(
                ticker = ticker,
                timeFrom = timeFrom,
                timeTo = timeTo
            )

            var saved = 0
            if (newsItems.isNotEmpty()) {
                saved = newsService.saveNewsItems(ticker, newsItems)
            }

            val duration = (System.currentTimeMillis() - startTime) / 1000.0

            logger.info {
                "Fetched and saved $saved items for $ticker in ${duration}s (found ${newsItems.size} total)"
            }

            return mapOf(
                "success" to true,
                "duration" to duration,
                "ticker" to ticker,
                "saved" to saved,
                "totalFound" to newsItems.size
            )

        } catch (e: Exception) {
            val duration = (System.currentTimeMillis() - startTime) / 1000.0
            logger.error(e) { "Failed to fetch news for $ticker" }
            return mapOf(
                "success" to false,
                "duration" to duration,
                "ticker" to ticker,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }
}
