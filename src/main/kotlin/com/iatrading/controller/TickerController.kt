package com.iatrading.controller

import com.iatrading.dto.*
import com.iatrading.entity.NewsStatus
import com.iatrading.exception.TickerNotFoundException
import com.iatrading.scheduler.AnalyzerScheduler
import com.iatrading.scheduler.NewsFetcherScheduler
import com.iatrading.service.NewsService
import com.iatrading.service.SentimentService
import com.iatrading.service.WatchlistService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/tickers")
@Tag(name = "Tickers", description = "Ticker watchlist management")
class TickerController(
    private val watchlistService: WatchlistService,
    private val newsService: NewsService,
    private val sentimentService: SentimentService,
    private val newsFetcherScheduler: NewsFetcherScheduler,
    private val analyzerScheduler: AnalyzerScheduler
) {
    // Virtual thread executor for background tasks
    private val executor: Executor = Executors.newVirtualThreadPerTaskExecutor()

    @GetMapping
    @Operation(summary = "Get all watched tickers with their sentiment")
    fun listTickers(): TickerListResponse {
        logger.info { "GET /api/tickers" }
        return watchlistService.getAllTickers()
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new ticker to the watchlist")
    fun createTicker(@Valid @RequestBody request: TickerCreateRequest): TickerResponse {
        logger.info { "POST /api/tickers - Creating ticker: ${request.ticker}" }
        return watchlistService.addTicker(request)
    }

    @DeleteMapping("/{tickerSymbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a ticker from the watchlist")
    fun deleteTicker(@PathVariable tickerSymbol: String) {
        logger.info { "DELETE /api/tickers/$tickerSymbol" }
        val removed = watchlistService.removeTicker(tickerSymbol)
        if (!removed) {
            throw TickerNotFoundException(tickerSymbol)
        }
    }

    @GetMapping("/{tickerSymbol}")
    @Operation(summary = "Get details for a specific ticker")
    fun getTickerDetail(@PathVariable tickerSymbol: String): TickerResponse {
        logger.info { "GET /api/tickers/$tickerSymbol" }
        return watchlistService.getTicker(tickerSymbol)
    }

    @GetMapping("/{tickerSymbol}/news")
    @Operation(summary = "Get news items for a ticker")
    fun getTickerNews(
        @PathVariable tickerSymbol: String,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): NewsListResponse {
        logger.info { "GET /api/tickers/$tickerSymbol/news (status=$status, limit=$limit, offset=$offset)" }

        // Verify ticker exists
        if (!watchlistService.tickerExists(tickerSymbol)) {
            throw TickerNotFoundException(tickerSymbol)
        }

        val newsStatus = status?.let {
            try {
                NewsStatus.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        return newsService.getNewsByTicker(tickerSymbol, newsStatus, limit, offset)
    }

    @GetMapping("/{tickerSymbol}/sentiment")
    @Operation(summary = "Get aggregated sentiment for a ticker")
    fun getTickerSentiment(@PathVariable tickerSymbol: String): TickerSentimentResponse {
        logger.info { "GET /api/tickers/$tickerSymbol/sentiment" }

        // Verify ticker exists
        if (!watchlistService.tickerExists(tickerSymbol)) {
            throw TickerNotFoundException(tickerSymbol)
        }

        return sentimentService.getTickerSentiment(tickerSymbol)
            ?: throw TickerNotFoundException("No sentiment data available for $tickerSymbol")
    }

    @PostMapping("/{tickerSymbol}/fetch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger news fetch for a specific ticker (runs in background)")
    fun triggerFetch(
        @PathVariable tickerSymbol: String,
        @RequestParam(defaultValue = "24") hours: Int
    ): ResponseEntity<Map<String, String>> {
        logger.info { "POST /api/tickers/$tickerSymbol/fetch (hours=$hours)" }

        // Verify ticker exists
        if (!watchlistService.tickerExists(tickerSymbol)) {
            throw TickerNotFoundException(tickerSymbol)
        }

        // Start background fetch
        CompletableFuture.runAsync({
            try {
                newsFetcherScheduler.fetchNewsForTicker(tickerSymbol, hours)
            } catch (e: Exception) {
                logger.error(e) { "Background fetch failed for $tickerSymbol" }
            }
        }, executor)

        return ResponseEntity.accepted().body(
            mapOf(
                "message" to "News fetch started for $tickerSymbol",
                "status" to "processing"
            )
        )
    }

    @PostMapping("/{tickerSymbol}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger sentiment analysis for pending news (runs in background)")
    fun triggerAnalyze(@PathVariable tickerSymbol: String): ResponseEntity<Map<String, String>> {
        logger.info { "POST /api/tickers/$tickerSymbol/analyze" }

        // Verify ticker exists
        if (!watchlistService.tickerExists(tickerSymbol)) {
            throw TickerNotFoundException(tickerSymbol)
        }

        // Start background analysis
        CompletableFuture.runAsync({
            try {
                analyzerScheduler.analyzePendingForTicker(tickerSymbol)
            } catch (e: Exception) {
                logger.error(e) { "Background analysis failed for $tickerSymbol" }
            }
        }, executor)

        return ResponseEntity.accepted().body(
            mapOf(
                "message" to "Analysis started for $tickerSymbol",
                "status" to "processing"
            )
        )
    }
}
