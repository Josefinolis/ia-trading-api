package com.iatrading.repository

import com.iatrading.entity.AnalysisRecord
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AnalysisRecordRepository : JpaRepository<AnalysisRecord, Long> {

    fun findByTickerIgnoreCaseOrderByAnalyzedAtDesc(ticker: String, pageable: Pageable): List<AnalysisRecord>

    fun findByAnalyzedAtBetween(startDate: LocalDateTime, endDate: LocalDateTime): List<AnalysisRecord>

    fun findByTickerIgnoreCaseAndAnalyzedAtBetween(
        ticker: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<AnalysisRecord>

    fun existsByTickerIgnoreCaseAndTitle(ticker: String, title: String): Boolean
}
