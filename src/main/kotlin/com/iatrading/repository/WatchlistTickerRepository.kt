package com.iatrading.repository

import com.iatrading.entity.WatchlistTicker
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface WatchlistTickerRepository : JpaRepository<WatchlistTicker, Long> {

    fun findByTicker(ticker: String): Optional<WatchlistTicker>

    fun findByTickerIgnoreCase(ticker: String): Optional<WatchlistTicker>

    fun existsByTickerIgnoreCase(ticker: String): Boolean

    fun findAllByIsActiveTrue(): List<WatchlistTicker>

    fun findAllByIsActiveTrueOrderByAddedAtDesc(): List<WatchlistTicker>

    @Query("SELECT w FROM WatchlistTicker w LEFT JOIN FETCH w.sentiment WHERE w.isActive = true ORDER BY w.addedAt DESC")
    fun findAllActiveWithSentiment(): List<WatchlistTicker>

    @Query("SELECT w FROM WatchlistTicker w LEFT JOIN FETCH w.sentiment WHERE w.ticker = :ticker")
    fun findByTickerWithSentiment(ticker: String): Optional<WatchlistTicker>
}
