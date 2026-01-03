package com.iatrading.client

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.iatrading.config.AppProperties
import com.iatrading.dto.NewsItemDto
import com.iatrading.exception.ExternalApiException
import com.iatrading.exception.RateLimitException
import com.iatrading.util.RateLimitManager
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Alpha Vantage API response models.
 */
data class AlphaVantageNewsResponse(
    val feed: List<AlphaVantageNewsItem>? = null,
    @SerializedName("Error Message")
    val errorMessage: String? = null,
    @SerializedName("Note")
    val note: String? = null,
    @SerializedName("Information")
    val information: String? = null
)

data class AlphaVantageNewsItem(
    val title: String,
    val summary: String,
    @SerializedName("time_published")
    val timePublished: String,
    val source: String? = null,
    val url: String? = null,
    @SerializedName("ticker_sentiment")
    val tickerSentiment: List<TickerSentimentItem>? = null
)

data class TickerSentimentItem(
    val ticker: String,
    @SerializedName("relevance_score")
    val relevanceScore: String? = null
)

/**
 * Client for Alpha Vantage News Sentiment API.
 */
@Component
class AlphaVantageClient(
    private val appProperties: AppProperties,
    private val rateLimitManager: RateLimitManager
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")

    /**
     * Check if the API is available.
     */
    fun isAvailable(): Boolean {
        if (appProperties.alphaVantage.apiKey.isBlank()) {
            logger.warn { "Alpha Vantage API key not configured" }
            return false
        }
        return rateLimitManager.alphaVantage.isAvailable()
    }

    /**
     * Fetch news for a ticker within a time range.
     */
    @Cacheable(value = ["alpha_vantage_news"], key = "#ticker + '_' + #timeFrom.toString() + '_' + #timeTo.toString()")
    fun fetchNews(
        ticker: String,
        timeFrom: LocalDateTime,
        timeTo: LocalDateTime
    ): List<NewsItemDto> {
        if (!isAvailable()) {
            val remaining = rateLimitManager.alphaVantage.getRemainingCooldown()
            if (remaining > 0) {
                throw RateLimitException("Alpha Vantage", remaining)
            }
            throw ExternalApiException("Alpha Vantage", "API not available")
        }

        val url = buildUrl(ticker, timeFrom, timeTo)
        logger.info { "Fetching news from Alpha Vantage for $ticker" }

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 429) {
                        rateLimitManager.alphaVantage.enterCooldown("Rate limit exceeded (429)", 60)
                        throw RateLimitException("Alpha Vantage", 60)
                    }
                    throw ExternalApiException("Alpha Vantage", "HTTP ${response.code}: ${response.message}")
                }

                val body = response.body?.string()
                    ?: throw ExternalApiException("Alpha Vantage", "Empty response")

                val apiResponse = gson.fromJson(body, AlphaVantageNewsResponse::class.java)

                // Check for API errors
                apiResponse.errorMessage?.let {
                    throw ExternalApiException("Alpha Vantage", it)
                }

                // Check for rate limit notes
                apiResponse.note?.let { note ->
                    if (isRateLimitMessage(note)) {
                        rateLimitManager.alphaVantage.enterCooldown(note, 60)
                        throw RateLimitException("Alpha Vantage", 60)
                    }
                    logger.warn { "Alpha Vantage note: $note" }
                }

                apiResponse.information?.let { info ->
                    if (isRateLimitMessage(info)) {
                        rateLimitManager.alphaVantage.enterCooldown(info, 60)
                        throw RateLimitException("Alpha Vantage", 60)
                    }
                }

                val newsItems = processResponse(apiResponse)
                logger.info { "Retrieved ${newsItems.size} news items from Alpha Vantage for $ticker" }
                return newsItems
            }

        } catch (e: RateLimitException) {
            throw e
        } catch (e: ExternalApiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error fetching news from Alpha Vantage" }
            throw ExternalApiException("Alpha Vantage", e.message ?: "Unknown error", e)
        }
    }

    private fun buildUrl(ticker: String, timeFrom: LocalDateTime, timeTo: LocalDateTime): String {
        val timeFromParam = timeFrom.format(dateFormatter)
        val timeToParam = timeTo.format(dateFormatter)

        return "${appProperties.alphaVantage.baseUrl}?function=NEWS_SENTIMENT" +
                "&tickers=$ticker" +
                "&apikey=${appProperties.alphaVantage.apiKey}" +
                "&time_from=$timeFromParam" +
                "&time_to=$timeToParam"
    }

    private fun processResponse(response: AlphaVantageNewsResponse): List<NewsItemDto> {
        val feed = response.feed ?: return emptyList()

        return feed.mapNotNull { item ->
            try {
                val relevanceScore = item.tickerSentiment?.firstOrNull()?.relevanceScore?.toDoubleOrNull()

                NewsItemDto(
                    title = item.title.trim(),
                    summary = item.summary.trim(),
                    publishedDate = item.timePublished,
                    source = item.source,
                    sourceType = "alpha_vantage",
                    url = item.url,
                    relevanceScore = relevanceScore
                )
            } catch (e: Exception) {
                logger.warn { "Skipping invalid news item: ${e.message}" }
                null
            }
        }
    }

    private fun isRateLimitMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return "rate limit" in lowerMessage ||
                "frequency" in lowerMessage ||
                "call volume" in lowerMessage
    }
}
