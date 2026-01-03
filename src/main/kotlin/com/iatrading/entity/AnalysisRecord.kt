package com.iatrading.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Entity for storing legacy analysis results.
 * Kept for backward compatibility with the Python implementation.
 */
@Entity
@Table(
    name = "analysis_results",
    indexes = [
        Index(name = "idx_analysis_ticker", columnList = "ticker"),
        Index(name = "idx_analysis_analyzed_at", columnList = "analyzed_at")
    ]
)
data class AnalysisRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 10)
    val ticker: String,

    @Column(nullable = false, length = 500)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val summary: String,

    @Column(name = "published_date", length = 50)
    val publishedDate: String? = null,

    @Column(length = 200)
    val source: String? = null,

    @Column(length = 500)
    val url: String? = null,

    @Column(name = "relevance_score")
    val relevanceScore: Double? = null,

    @Column(length = 20)
    val sentiment: String? = null,

    @Column(columnDefinition = "TEXT")
    val justification: String? = null,

    @Column(name = "analyzed_at", nullable = false)
    val analyzedAt: LocalDateTime = LocalDateTime.now(),

    @Column(columnDefinition = "TEXT")
    val error: String? = null
) {
    constructor() : this(ticker = "", title = "", summary = "")
}
