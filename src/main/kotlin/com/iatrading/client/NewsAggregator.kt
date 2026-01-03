package com.iatrading.client

import com.iatrading.dto.NewsItemDto
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Aggregates news from multiple sources with deduplication.
 */
@Component
class NewsAggregator(
    private val alphaVantageClient: AlphaVantageClient,
    private val redditClient: RedditClient
) {
    // Use virtual threads for parallel fetching
    private val executor: Executor = Executors.newVirtualThreadPerTaskExecutor()

    // Source priorities for deduplication
    private val sourcePriorities = mapOf(
        "alpha_vantage" to 3,
        "reddit" to 2,
        "twitter" to 1
    )

    // Timeout for each source in seconds
    private val scraperTimeout = 30L

    // Similarity threshold for title deduplication
    private val similarityThreshold = 0.85

    /**
     * Fetch news from all available sources.
     */
    fun fetchAll(
        ticker: String,
        timeFrom: LocalDateTime,
        timeTo: LocalDateTime,
        sourcesFilter: List<String>? = null
    ): List<NewsItemDto> {
        logger.info { "Aggregating news for $ticker from ${timeFrom} to ${timeTo}" }

        val allNews = mutableListOf<NewsItemDto>()
        val futures = mutableListOf<CompletableFuture<List<NewsItemDto>>>()

        // Fetch from Alpha Vantage
        if (sourcesFilter == null || "alpha_vantage" in sourcesFilter) {
            if (alphaVantageClient.isAvailable()) {
                futures.add(
                    CompletableFuture.supplyAsync({
                        try {
                            alphaVantageClient.fetchNews(ticker, timeFrom, timeTo)
                        } catch (e: Exception) {
                            logger.warn { "Error fetching from Alpha Vantage: ${e.message}" }
                            emptyList()
                        }
                    }, executor)
                )
            }
        }

        // Fetch from Reddit
        if (sourcesFilter == null || "reddit" in sourcesFilter) {
            if (redditClient.isAvailable()) {
                futures.add(
                    CompletableFuture.supplyAsync({
                        try {
                            redditClient.fetchNews(ticker, timeFrom, timeTo)
                        } catch (e: Exception) {
                            logger.warn { "Error fetching from Reddit: ${e.message}" }
                            emptyList()
                        }
                    }, executor)
                )
            }
        }

        // Wait for all futures with timeout
        for (future in futures) {
            try {
                val news = future.get(scraperTimeout, TimeUnit.SECONDS)
                allNews.addAll(news)
            } catch (e: Exception) {
                logger.warn { "Timeout or error fetching from source: ${e.message}" }
            }
        }

        // Deduplicate
        val deduplicated = deduplicate(allNews)

        // Sort by date (newest first)
        val sorted = sortByDate(deduplicated)

        logger.info {
            "Aggregated ${sorted.size} news items for $ticker (from ${allNews.size} raw items)"
        }

        return sorted
    }

    /**
     * Get list of available source names.
     */
    fun getAvailableSources(): List<String> {
        val sources = mutableListOf<String>()
        if (alphaVantageClient.isAvailable()) sources.add("alpha_vantage")
        if (redditClient.isAvailable()) sources.add("reddit")
        return sources
    }

    private fun deduplicate(newsItems: List<NewsItemDto>): List<NewsItemDto> {
        if (newsItems.isEmpty()) return emptyList()

        val uniqueItems = mutableListOf<NewsItemDto>()
        val seenUrls = mutableSetOf<String>()

        for (item in newsItems) {
            // Skip if URL already seen
            if (item.url != null && item.url in seenUrls) {
                continue
            }

            // Check for similar titles
            var isDuplicate = false
            for ((index, existing) in uniqueItems.withIndex()) {
                if (isSimilar(item.title, existing.title)) {
                    // Keep the higher priority one
                    if (getPriority(item) > getPriority(existing)) {
                        existing.url?.let { seenUrls.remove(it) }
                        uniqueItems[index] = item
                        item.url?.let { seenUrls.add(it) }
                    }
                    isDuplicate = true
                    break
                }
            }

            if (!isDuplicate) {
                uniqueItems.add(item)
                item.url?.let { seenUrls.add(it) }
            }
        }

        return uniqueItems
    }

    private fun isSimilar(title1: String, title2: String): Boolean {
        val t1 = title1.lowercase().trim()
        val t2 = title2.lowercase().trim()

        if (t1 == t2) return true

        // Simple Jaccard similarity on words
        val words1 = t1.split(Regex("\\s+")).toSet()
        val words2 = t2.split(Regex("\\s+")).toSet()

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size

        return if (union > 0) {
            intersection.toDouble() / union >= similarityThreshold
        } else {
            false
        }
    }

    private fun getPriority(item: NewsItemDto): Double {
        var priority = sourcePriorities[item.sourceType] ?: 0
        item.relevanceScore?.let { priority += it.toInt() }
        return priority.toDouble()
    }

    private fun sortByDate(newsItems: List<NewsItemDto>): List<NewsItemDto> {
        return newsItems.sortedByDescending { item ->
            try {
                parseDate(item.publishedDate)
            } catch (e: Exception) {
                LocalDateTime.MIN
            }
        }
    }

    private fun parseDate(dateStr: String): LocalDateTime {
        val formats = listOf(
            "yyyyMMdd'T'HHmmss",
            "yyyyMMdd'T'HHmm",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        for (format in formats) {
            try {
                return LocalDateTime.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern(format))
            } catch (e: Exception) {
                // Try next format
            }
        }

        return LocalDateTime.MIN
    }
}
