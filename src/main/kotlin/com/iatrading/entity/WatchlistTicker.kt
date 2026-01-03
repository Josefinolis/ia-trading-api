package com.iatrading.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Entity representing a ticker being watched/followed.
 * Corresponds to Python's WatchlistTicker model.
 */
@Entity
@Table(name = "watchlist_tickers")
data class WatchlistTicker(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 10)
    val ticker: String,

    @Column(length = 200)
    val name: String? = null,

    @Column(name = "added_at", nullable = false)
    val addedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @OneToMany(mappedBy = "watchlistTicker", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val newsItems: MutableList<NewsRecord> = mutableListOf(),

    @OneToOne(mappedBy = "watchlistTicker", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var sentiment: TickerSentiment? = null
) {
    // JPA requires a no-arg constructor for entities
    constructor() : this(ticker = "")
}
