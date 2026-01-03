package com.iatrading.dto

import com.iatrading.entity.TickerSentiment
import com.iatrading.entity.WatchlistTicker
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * Request DTO for creating a new ticker.
 */
data class TickerCreateRequest(
    @field:NotBlank(message = "Ticker symbol is required")
    @field:Size(min = 1, max = 10, message = "Ticker must be between 1 and 10 characters")
    @field:Pattern(regexp = "^[A-Z]+$", message = "Ticker must contain only uppercase letters")
    val ticker: String,

    @field:Size(max = 200, message = "Name must not exceed 200 characters")
    val name: String? = null
)

/**
 * Response DTO for ticker sentiment data.
 */
data class TickerSentimentResponse(
    val ticker: String,
    val score: Double,
    val normalizedScore: Double,
    val sentimentLabel: String?,
    val signal: String?,
    val confidence: Double,
    val positiveCount: Int,
    val negativeCount: Int,
    val neutralCount: Int,
    val totalAnalyzed: Int,
    val totalPending: Int,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(entity: TickerSentiment): TickerSentimentResponse {
            return TickerSentimentResponse(
                ticker = entity.ticker,
                score = entity.score,
                normalizedScore = entity.normalizedScore,
                sentimentLabel = entity.sentimentLabel,
                signal = entity.signal,
                confidence = entity.confidence,
                positiveCount = entity.positiveCount,
                negativeCount = entity.negativeCount,
                neutralCount = entity.neutralCount,
                totalAnalyzed = entity.totalAnalyzed,
                totalPending = entity.totalPending,
                updatedAt = entity.updatedAt
            )
        }
    }
}

/**
 * Response DTO for a ticker with its sentiment.
 */
data class TickerResponse(
    val id: Long,
    val ticker: String,
    val name: String?,
    val addedAt: LocalDateTime,
    val isActive: Boolean,
    val sentiment: TickerSentimentResponse?
) {
    companion object {
        fun from(entity: WatchlistTicker): TickerResponse {
            return TickerResponse(
                id = entity.id,
                ticker = entity.ticker,
                name = entity.name,
                addedAt = entity.addedAt,
                isActive = entity.isActive,
                sentiment = entity.sentiment?.let { TickerSentimentResponse.from(it) }
            )
        }
    }
}

/**
 * Response DTO for list of tickers.
 */
data class TickerListResponse(
    val tickers: List<TickerResponse>,
    val count: Int
)
