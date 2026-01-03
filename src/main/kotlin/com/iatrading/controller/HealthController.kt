package com.iatrading.controller

import com.iatrading.dto.*
import com.iatrading.util.RateLimitManager
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

@RestController
@Tag(name = "Health", description = "Health check and status endpoints")
class HealthController(
    private val dataSource: DataSource,
    private val rateLimitManager: RateLimitManager,
    @Value("\${spring.application.name:ia-trading-api}") private val appName: String
) {

    @GetMapping("/")
    @Operation(summary = "Root endpoint")
    fun root(): Map<String, String> {
        return mapOf(
            "name" to "IA Trading API",
            "version" to "1.0.0",
            "docs" to "/swagger-ui.html"
        )
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint - lightweight, no DB call")
    fun healthCheck(): HealthResponse {
        return HealthResponse(
            status = "healthy",
            version = "1.0.0",
            database = "unknown", // Don't check DB in lightweight health endpoint
            scheduler = "unknown"
        )
    }

    @GetMapping("/health/full")
    @Operation(summary = "Full health check with database verification")
    fun healthCheckFull(): HealthResponse {
        // Test database connection
        val dbStatus = try {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT 1")
                }
            }
            "connected"
        } catch (e: Exception) {
            logger.error(e) { "Database health check failed" }
            "error"
        }

        val status = if (dbStatus == "connected") "healthy" else "degraded"

        return HealthResponse(
            status = status,
            version = "1.0.0",
            database = dbStatus,
            scheduler = "unknown" // Will be updated when scheduler is implemented
        )
    }

    @GetMapping("/api/status")
    @Operation(summary = "Get API rate limit status for all services")
    fun apiStatus(): ApiStatusResponse {
        val allStatus = rateLimitManager.getAllStatus()

        return ApiStatusResponse(
            gemini = ApiServiceStatus(
                available = allStatus["gemini"]?.get("available") as? Boolean ?: false,
                cooldownUntil = allStatus["gemini"]?.get("cooldownUntil") as? String,
                message = allStatus["gemini"]?.get("message") as? String
            ),
            alphaVantage = ApiServiceStatus(
                available = allStatus["alpha_vantage"]?.get("available") as? Boolean ?: false,
                cooldownUntil = allStatus["alpha_vantage"]?.get("cooldownUntil") as? String,
                message = allStatus["alpha_vantage"]?.get("message") as? String
            )
        )
    }

    @GetMapping("/api/scheduler/status")
    @Operation(summary = "Get scheduler status and job information")
    fun schedulerStatus(): SchedulerStatusResponse {
        // Placeholder - will be implemented when scheduler is added
        return SchedulerStatusResponse(
            running = false,
            jobs = emptyList()
        )
    }

    @GetMapping("/debug/status")
    @Operation(summary = "Debug endpoint showing system resource usage")
    fun debugStatus(): Map<String, Any> {
        val runtime = Runtime.getRuntime()

        return mapOf(
            "memory" to mapOf(
                "totalMb" to runtime.totalMemory() / 1024 / 1024,
                "freeMb" to runtime.freeMemory() / 1024 / 1024,
                "usedMb" to (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024,
                "maxMb" to runtime.maxMemory() / 1024 / 1024
            ),
            "threads" to mapOf(
                "count" to Thread.activeCount()
            ),
            "process" to mapOf(
                "availableProcessors" to runtime.availableProcessors()
            )
        )
    }
}
