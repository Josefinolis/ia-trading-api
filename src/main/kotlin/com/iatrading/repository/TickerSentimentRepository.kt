package com.iatrading.repository

import com.iatrading.entity.TickerSentiment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TickerSentimentRepository : JpaRepository<TickerSentiment, Long> {

    fun findByTicker(ticker: String): Optional<TickerSentiment>

    fun findByTickerIgnoreCase(ticker: String): Optional<TickerSentiment>

    fun existsByTickerIgnoreCase(ticker: String): Boolean
}
