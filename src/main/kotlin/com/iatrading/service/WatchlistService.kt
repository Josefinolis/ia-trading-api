package com.iatrading.service

import com.iatrading.dto.TickerCreateRequest
import com.iatrading.dto.TickerListResponse
import com.iatrading.dto.TickerResponse
import com.iatrading.entity.TickerSentiment
import com.iatrading.entity.WatchlistTicker
import com.iatrading.exception.TickerNotFoundException
import com.iatrading.repository.TickerSentimentRepository
import com.iatrading.repository.WatchlistTickerRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class WatchlistService(
    private val watchlistTickerRepository: WatchlistTickerRepository,
    private val tickerSentimentRepository: TickerSentimentRepository
) {

    /**
     * Get all active tickers with their sentiment data.
     */
    @Transactional(readOnly = true)
    fun getAllTickers(includeInactive: Boolean = false): TickerListResponse {
        logger.debug { "Fetching all tickers (includeInactive=$includeInactive)" }

        val tickers = if (includeInactive) {
            watchlistTickerRepository.findAll()
        } else {
            watchlistTickerRepository.findAllActiveWithSentiment()
        }

        val responses = tickers.map { TickerResponse.from(it) }

        logger.info { "Found ${responses.size} tickers" }
        return TickerListResponse(tickers = responses, count = responses.size)
    }

    /**
     * Get a specific ticker by symbol.
     */
    @Transactional(readOnly = true)
    fun getTicker(tickerSymbol: String): TickerResponse {
        val ticker = watchlistTickerRepository.findByTickerWithSentiment(tickerSymbol.uppercase())
            .orElseThrow { TickerNotFoundException(tickerSymbol) }

        return TickerResponse.from(ticker)
    }

    /**
     * Check if a ticker exists.
     */
    fun tickerExists(tickerSymbol: String): Boolean {
        return watchlistTickerRepository.existsByTickerIgnoreCase(tickerSymbol)
    }

    /**
     * Add a new ticker to the watchlist.
     */
    @Transactional
    fun addTicker(request: TickerCreateRequest): TickerResponse {
        val tickerSymbol = request.ticker.uppercase()
        logger.info { "Adding ticker: $tickerSymbol" }

        // Check if ticker already exists
        val existing = watchlistTickerRepository.findByTickerIgnoreCase(tickerSymbol).orElse(null)

        if (existing != null) {
            // Reactivate if inactive
            if (!existing.isActive) {
                existing.isActive = true
                watchlistTickerRepository.save(existing)
                logger.info { "Reactivated ticker: $tickerSymbol" }
            }
            return TickerResponse.from(existing)
        }

        // Create new ticker
        val ticker = WatchlistTicker(
            ticker = tickerSymbol,
            name = request.name,
            addedAt = LocalDateTime.now(),
            isActive = true
        )
        val savedTicker = watchlistTickerRepository.save(ticker)

        // Create empty sentiment record
        val sentiment = TickerSentiment(
            ticker = tickerSymbol,
            score = 0.0,
            normalizedScore = 0.0,
            confidence = 0.0,
            positiveCount = 0,
            negativeCount = 0,
            neutralCount = 0,
            totalAnalyzed = 0,
            totalPending = 0
        )
        tickerSentimentRepository.save(sentiment)

        // Refresh to load sentiment
        val refreshedTicker = watchlistTickerRepository.findByTickerWithSentiment(tickerSymbol)
            .orElse(savedTicker)

        logger.info { "Added new ticker: $tickerSymbol" }
        return TickerResponse.from(refreshedTicker)
    }

    /**
     * Remove (deactivate) a ticker from the watchlist.
     */
    @Transactional
    fun removeTicker(tickerSymbol: String): Boolean {
        val ticker = watchlistTickerRepository.findByTickerIgnoreCase(tickerSymbol.uppercase())
            .orElse(null) ?: return false

        ticker.isActive = false
        watchlistTickerRepository.save(ticker)
        logger.info { "Deactivated ticker: ${ticker.ticker}" }
        return true
    }

    /**
     * Get entity for internal use (not exposed via API).
     */
    @Transactional(readOnly = true)
    fun getTickerEntity(tickerSymbol: String): WatchlistTicker? {
        return watchlistTickerRepository.findByTickerIgnoreCase(tickerSymbol.uppercase()).orElse(null)
    }

    /**
     * Get all active ticker entities.
     */
    @Transactional(readOnly = true)
    fun getAllActiveTickerEntities(): List<WatchlistTicker> {
        return watchlistTickerRepository.findAllByIsActiveTrue()
    }
}
