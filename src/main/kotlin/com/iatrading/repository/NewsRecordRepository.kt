package com.iatrading.repository

import com.iatrading.entity.NewsRecord
import com.iatrading.entity.NewsStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface NewsRecordRepository : JpaRepository<NewsRecord, Long> {

    fun findByTicker(ticker: String): List<NewsRecord>

    fun findByTickerOrderByFetchedAtDesc(ticker: String, pageable: Pageable): List<NewsRecord>

    fun findByTickerAndStatusOrderByFetchedAtDesc(
        ticker: String,
        status: NewsStatus,
        pageable: Pageable
    ): List<NewsRecord>

    fun findByTickerIgnoreCaseOrderByFetchedAtDesc(ticker: String, pageable: Pageable): List<NewsRecord>

    fun findByTickerIgnoreCaseAndStatusOrderByFetchedAtDesc(
        ticker: String,
        status: NewsStatus,
        pageable: Pageable
    ): List<NewsRecord>

    fun existsByUrl(url: String): Boolean

    fun countByTickerIgnoreCaseAndStatus(ticker: String, status: NewsStatus): Long

    @Query("SELECT n FROM NewsRecord n WHERE n.status = :status ORDER BY n.fetchedAt ASC")
    fun findPendingNews(status: NewsStatus = NewsStatus.PENDING, pageable: Pageable): List<NewsRecord>

    fun findByTickerIgnoreCaseAndStatusOrderByFetchedAtAsc(
        ticker: String,
        status: NewsStatus,
        pageable: Pageable
    ): List<NewsRecord>

    @Query("SELECT n FROM NewsRecord n WHERE n.status = 'PENDING' ORDER BY n.fetchedAt ASC")
    fun findAllPendingOrderByFetchedAtAsc(pageable: Pageable): List<NewsRecord>

    @Query("""
        SELECT n FROM NewsRecord n
        WHERE n.ticker = :ticker
        AND n.status = 'ANALYZED'
        AND n.sentiment IS NOT NULL
    """)
    fun findAnalyzedWithSentiment(ticker: String): List<NewsRecord>
}
