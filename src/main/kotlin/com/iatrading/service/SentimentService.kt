package com.iatrading.service

import com.iatrading.dto.TickerSentimentResponse
import com.iatrading.entity.NewsStatus
import com.iatrading.entity.SentimentCategory
import com.iatrading.entity.TickerSentiment
import com.iatrading.repository.NewsRecordRepository
import com.iatrading.repository.TickerSentimentRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class SentimentService(
    private val tickerSentimentRepository: TickerSentimentRepository,
    private val newsRecordRepository: NewsRecordRepository
) {

    /**
     * Get aggregated sentiment for a ticker.
     */
    @Transactional(readOnly = true)
    fun getTickerSentiment(tickerSymbol: String): TickerSentimentResponse? {
        val sentiment = tickerSentimentRepository.findByTickerIgnoreCase(tickerSymbol.uppercase())
            .orElse(null) ?: return null

        return TickerSentimentResponse.from(sentiment)
    }

    /**
     * Recalculate and update aggregated sentiment for a ticker.
     */
    @Transactional
    fun updateTickerSentiment(tickerSymbol: String): TickerSentiment? {
        val ticker = tickerSymbol.uppercase()
        logger.debug { "Updating sentiment for $ticker" }

        // Get or create sentiment record
        var sentiment = tickerSentimentRepository.findByTickerIgnoreCase(ticker).orElse(null)
        if (sentiment == null) {
            sentiment = TickerSentiment(ticker = ticker)
            tickerSentimentRepository.save(sentiment)
        }

        // Count pending news
        val pendingCount = newsRecordRepository.countByTickerIgnoreCaseAndStatus(ticker, NewsStatus.PENDING)

        // Get analyzed news with sentiment
        val analyzedNews = newsRecordRepository.findAnalyzedWithSentiment(ticker)

        // Count sentiments
        var positiveCount = 0
        var negativeCount = 0
        var neutralCount = 0
        var totalScore = 0.0

        for (news in analyzedNews) {
            val score = SentimentCategory.getScore(news.sentiment ?: "Neutral")
            totalScore += score

            when {
                score > 0 -> positiveCount++
                score < 0 -> negativeCount++
                else -> neutralCount++
            }
        }

        val totalAnalyzed = analyzedNews.size

        // Calculate normalized score (-1 to 1)
        val normalizedScore = if (totalAnalyzed > 0) {
            totalScore / totalAnalyzed
        } else {
            0.0
        }

        // Calculate confidence based on agreement
        val confidence = if (totalAnalyzed > 0) {
            val maxCount = maxOf(positiveCount, negativeCount, neutralCount)
            maxCount.toDouble() / totalAnalyzed
        } else {
            0.0
        }

        // Update sentiment record
        sentiment.apply {
            this.score = totalScore
            this.normalizedScore = normalizedScore.roundTo(4)
            this.confidence = confidence.roundTo(4)
            this.positiveCount = positiveCount
            this.negativeCount = negativeCount
            this.neutralCount = neutralCount
            this.totalAnalyzed = totalAnalyzed
            this.totalPending = pendingCount.toInt()
            this.updatedAt = LocalDateTime.now()
            updateSentimentLabel()
            updateSignal()
        }

        val saved = tickerSentimentRepository.save(sentiment)

        logger.info {
            "Updated sentiment for $ticker: score=${normalizedScore.roundTo(2)}, signal=${saved.signal}"
        }

        return saved
    }

    /**
     * Extension function to round a Double to N decimal places.
     */
    private fun Double.roundTo(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
