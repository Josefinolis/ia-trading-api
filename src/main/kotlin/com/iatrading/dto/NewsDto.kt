package com.iatrading.dto

import com.iatrading.entity.NewsRecord
import com.iatrading.entity.NewsStatus
import java.time.LocalDateTime

/**
 * Response DTO for a news item.
 */
data class NewsItemResponse(
    val id: Long,
    val ticker: String,
    val title: String,
    val summary: String,
    val publishedDate: String?,
    val source: String?,
    val sourceType: String?,
    val url: String?,
    val relevanceScore: Double?,
    val engagementScore: Int?,
    val author: String?,
    val status: NewsStatus,
    val sentiment: String?,
    val justification: String?,
    val fetchedAt: LocalDateTime,
    val analyzedAt: LocalDateTime?
) {
    companion object {
        fun from(entity: NewsRecord): NewsItemResponse {
            return NewsItemResponse(
                id = entity.id,
                ticker = entity.ticker,
                title = entity.title,
                summary = entity.summary,
                publishedDate = entity.publishedDate,
                source = entity.source,
                sourceType = entity.sourceType,
                url = entity.url,
                relevanceScore = entity.relevanceScore,
                engagementScore = entity.engagementScore,
                author = entity.author,
                status = entity.status,
                sentiment = entity.sentiment,
                justification = entity.justification,
                fetchedAt = entity.fetchedAt,
                analyzedAt = entity.analyzedAt
            )
        }
    }
}

/**
 * Response DTO for list of news.
 */
data class NewsListResponse(
    val news: List<NewsItemResponse>,
    val count: Int,
    val pendingCount: Int,
    val analyzedCount: Int
)

/**
 * Internal DTO for news items from external sources.
 */
data class NewsItemDto(
    val title: String,
    val summary: String,
    val publishedDate: String,
    val source: String?,
    val sourceType: String,
    val url: String?,
    val relevanceScore: Double?,
    val engagementScore: Int? = null,
    val author: String? = null,
    val authorFollowers: Int? = null
)
