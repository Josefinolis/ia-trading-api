package com.iatrading.entity

import jakarta.persistence.*
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.LocalDateTime

/**
 * Entity representing a news item with analysis status tracking.
 * Corresponds to Python's NewsRecord model.
 */
@Entity
@Table(
    name = "news_records",
    indexes = [
        Index(name = "idx_news_ticker", columnList = "ticker"),
        Index(name = "idx_news_url", columnList = "url"),
        Index(name = "idx_news_status", columnList = "status")
    ]
)
data class NewsRecord(
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

    @Column(name = "source_type", length = 50)
    val sourceType: String? = null,

    @Column(length = 500, unique = true)
    val url: String? = null,

    @Column(name = "relevance_score")
    val relevanceScore: Double? = null,

    @Column(name = "engagement_score")
    val engagementScore: Int? = null,

    @Column(length = 100)
    val author: String? = null,

    // Status tracking
    @Column(nullable = false, length = 20)
    var status: NewsStatus = NewsStatus.PENDING,

    @Column(name = "fetched_at", nullable = false)
    val fetchedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "analyzed_at")
    var analyzedAt: LocalDateTime? = null,

    // Analysis results (populated after analysis)
    @Column(length = 30)
    var sentiment: String? = null,

    @Column(columnDefinition = "TEXT")
    var justification: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticker", referencedColumnName = "ticker", insertable = false, updatable = false)
    val watchlistTicker: WatchlistTicker? = null
) {
    constructor() : this(ticker = "", title = "", summary = "")
}

enum class NewsStatus {
    PENDING,
    ANALYZED;

    companion object {
        fun fromString(value: String): NewsStatus {
            return when (value.lowercase()) {
                "pending" -> PENDING
                "analyzed" -> ANALYZED
                else -> throw IllegalArgumentException("Unknown status: $value")
            }
        }
    }

    fun toDbValue(): String = name.lowercase()
}

/**
 * JPA Converter for NewsStatus to handle lowercase values from Python API.
 */
@Converter(autoApply = true)
class NewsStatusConverter : AttributeConverter<NewsStatus, String> {
    override fun convertToDatabaseColumn(attribute: NewsStatus?): String? {
        return attribute?.toDbValue()
    }

    override fun convertToEntityAttribute(dbData: String?): NewsStatus? {
        return dbData?.let { NewsStatus.fromString(it) }
    }
}
