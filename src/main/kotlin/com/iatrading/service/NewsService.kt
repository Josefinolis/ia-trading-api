package com.iatrading.service

import com.iatrading.dto.NewsItemDto
import com.iatrading.dto.NewsItemResponse
import com.iatrading.dto.NewsListResponse
import com.iatrading.entity.NewsRecord
import com.iatrading.entity.NewsStatus
import com.iatrading.repository.NewsRecordRepository
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class NewsService(
    private val newsRecordRepository: NewsRecordRepository
) {

    /**
     * Get news items for a ticker with optional status filter.
     */
    @Transactional(readOnly = true)
    fun getNewsByTicker(
        tickerSymbol: String,
        status: NewsStatus? = null,
        limit: Int = 50,
        offset: Int = 0
    ): NewsListResponse {
        val pageable = PageRequest.of(offset / limit, limit)
        val ticker = tickerSymbol.uppercase()

        val newsItems = if (status != null) {
            newsRecordRepository.findByTickerIgnoreCaseAndStatusOrderByFetchedAtDesc(
                ticker, status, pageable
            )
        } else {
            newsRecordRepository.findByTickerIgnoreCaseOrderByFetchedAtDesc(ticker, pageable)
        }

        val pendingCount = newsRecordRepository.countByTickerIgnoreCaseAndStatus(ticker, NewsStatus.PENDING)
        val analyzedCount = newsRecordRepository.countByTickerIgnoreCaseAndStatus(ticker, NewsStatus.ANALYZED)

        val responses = newsItems.map { NewsItemResponse.from(it) }

        return NewsListResponse(
            news = responses,
            count = responses.size,
            pendingCount = pendingCount.toInt(),
            analyzedCount = analyzedCount.toInt()
        )
    }

    /**
     * Get counts of pending and analyzed news for a ticker.
     */
    @Transactional(readOnly = true)
    fun getNewsCounts(tickerSymbol: String): Map<String, Int> {
        val ticker = tickerSymbol.uppercase()
        val pending = newsRecordRepository.countByTickerIgnoreCaseAndStatus(ticker, NewsStatus.PENDING)
        val analyzed = newsRecordRepository.countByTickerIgnoreCaseAndStatus(ticker, NewsStatus.ANALYZED)

        return mapOf(
            "pending" to pending.toInt(),
            "analyzed" to analyzed.toInt(),
            "total" to (pending + analyzed).toInt()
        )
    }

    /**
     * Save a news item to the database if not a duplicate.
     */
    @Transactional
    fun saveNewsItem(tickerSymbol: String, news: NewsItemDto): NewsRecord? {
        val ticker = tickerSymbol.uppercase()

        // Check for duplicate by URL
        if (news.url != null && newsRecordRepository.existsByUrl(news.url)) {
            logger.debug { "Skipping duplicate news: ${news.url}" }
            return null
        }

        val record = NewsRecord(
            ticker = ticker,
            title = news.title,
            summary = news.summary,
            publishedDate = news.publishedDate,
            source = news.source,
            sourceType = news.sourceType,
            url = news.url,
            relevanceScore = news.relevanceScore,
            engagementScore = news.engagementScore,
            author = news.author,
            status = NewsStatus.PENDING,
            fetchedAt = LocalDateTime.now()
        )

        val saved = newsRecordRepository.save(record)
        logger.debug { "Saved news item: ${news.title.take(50)}..." }
        return saved
    }

    /**
     * Save multiple news items.
     */
    @Transactional
    fun saveNewsItems(tickerSymbol: String, newsItems: List<NewsItemDto>): Int {
        var savedCount = 0
        for (news in newsItems) {
            if (saveNewsItem(tickerSymbol, news) != null) {
                savedCount++
            }
        }
        logger.info { "Saved $savedCount/${newsItems.size} news items for $tickerSymbol" }
        return savedCount
    }

    /**
     * Get pending news items for analysis.
     */
    @Transactional(readOnly = true)
    fun getPendingNews(limit: Int = 10): List<NewsRecord> {
        val pageable = PageRequest.of(0, limit)
        return newsRecordRepository.findAllPendingOrderByFetchedAtAsc(pageable)
    }

    /**
     * Get pending news items for a specific ticker.
     */
    @Transactional(readOnly = true)
    fun getPendingNewsForTicker(tickerSymbol: String, limit: Int = 100): List<NewsRecord> {
        val pageable = PageRequest.of(0, limit)
        return newsRecordRepository.findByTickerIgnoreCaseAndStatusOrderByFetchedAtAsc(
            tickerSymbol.uppercase(),
            NewsStatus.PENDING,
            pageable
        )
    }

    /**
     * Update a news item with analysis results.
     */
    @Transactional
    fun updateNewsAnalysis(newsId: Long, sentiment: String, justification: String): Boolean {
        val news = newsRecordRepository.findById(newsId).orElse(null) ?: return false

        news.sentiment = sentiment
        news.justification = justification
        news.status = NewsStatus.ANALYZED
        news.analyzedAt = LocalDateTime.now()

        newsRecordRepository.save(news)
        logger.debug { "Updated news analysis: $newsId -> $sentiment" }
        return true
    }

    /**
     * Get analyzed news with sentiment for a ticker.
     */
    @Transactional(readOnly = true)
    fun getAnalyzedNewsWithSentiment(tickerSymbol: String): List<NewsRecord> {
        return newsRecordRepository.findAnalyzedWithSentiment(tickerSymbol.uppercase())
    }
}
