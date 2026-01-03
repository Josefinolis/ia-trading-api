package com.iatrading.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Entity representing aggregated sentiment per ticker.
 * Corresponds to Python's TickerSentiment model.
 */
@Entity
@Table(
    name = "ticker_sentiments",
    indexes = [
        Index(name = "idx_sentiment_ticker", columnList = "ticker")
    ]
)
data class TickerSentiment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 10)
    val ticker: String,

    // Sentiment scores
    @Column(nullable = false)
    var score: Double = 0.0,

    @Column(name = "normalized_score", nullable = false)
    var normalizedScore: Double = 0.0, // -1 to 1 scale

    @Column(name = "sentiment_label", length = 30)
    var sentimentLabel: String? = null, // "Highly Positive", etc.

    @Column(length = 20)
    var signal: String? = null, // "STRONG BUY", "BUY", "HOLD", "SELL", "STRONG SELL"

    @Column(nullable = false)
    var confidence: Double = 0.0,

    // Counts
    @Column(name = "positive_count", nullable = false)
    var positiveCount: Int = 0,

    @Column(name = "negative_count", nullable = false)
    var negativeCount: Int = 0,

    @Column(name = "neutral_count", nullable = false)
    var neutralCount: Int = 0,

    @Column(name = "total_analyzed", nullable = false)
    var totalAnalyzed: Int = 0,

    @Column(name = "total_pending", nullable = false)
    var totalPending: Int = 0,

    // Timestamps
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker", referencedColumnName = "ticker", insertable = false, updatable = false)
    val watchlistTicker: WatchlistTicker? = null
) {
    constructor() : this(ticker = "")

    /**
     * Calculate and update the trading signal based on normalized score.
     */
    fun updateSignal() {
        signal = when {
            normalizedScore >= 0.5 -> "STRONG BUY"
            normalizedScore >= 0.2 -> "BUY"
            normalizedScore >= -0.2 -> "HOLD"
            normalizedScore >= -0.5 -> "SELL"
            else -> "STRONG SELL"
        }
    }

    /**
     * Calculate and update the sentiment label based on normalized score.
     */
    fun updateSentimentLabel() {
        sentimentLabel = when {
            normalizedScore >= 0.5 -> SentimentCategory.HIGHLY_POSITIVE.label
            normalizedScore >= 0.2 -> SentimentCategory.POSITIVE.label
            normalizedScore >= -0.2 -> SentimentCategory.NEUTRAL.label
            normalizedScore >= -0.5 -> SentimentCategory.NEGATIVE.label
            else -> SentimentCategory.HIGHLY_NEGATIVE.label
        }
    }
}

/**
 * Sentiment categories matching the Python implementation.
 */
enum class SentimentCategory(val label: String, val score: Double) {
    HIGHLY_NEGATIVE("Highly Negative", -1.0),
    NEGATIVE("Negative", -0.5),
    NEUTRAL("Neutral", 0.0),
    POSITIVE("Positive", 0.5),
    HIGHLY_POSITIVE("Highly Positive", 1.0);

    companion object {
        fun fromLabel(label: String): SentimentCategory? =
            entries.find { it.label.equals(label, ignoreCase = true) }

        fun getScore(label: String): Double =
            fromLabel(label)?.score ?: 0.0
    }
}

/**
 * Trading signals.
 */
enum class TradingSignal(val label: String) {
    STRONG_BUY("STRONG BUY"),
    BUY("BUY"),
    HOLD("HOLD"),
    SELL("SELL"),
    STRONG_SELL("STRONG SELL")
}
