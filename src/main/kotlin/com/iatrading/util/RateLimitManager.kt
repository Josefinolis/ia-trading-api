package com.iatrading.util

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * Rate limit status for an API service.
 */
class RateLimitStatus(
    val serviceName: String,
    private val defaultCooldownSeconds: Int = 60
) {
    private var cooldownUntil: LocalDateTime? = null
    private var message: String? = null
    private val lock = ReentrantLock()

    /**
     * Check if the service is available (not in cooldown).
     */
    fun isAvailable(): Boolean = lock.withLock {
        val until = cooldownUntil ?: return true
        if (LocalDateTime.now() >= until) {
            clearCooldown()
            return true
        }
        return false
    }

    /**
     * Put the service into cooldown mode.
     */
    fun enterCooldown(reason: String, cooldownSeconds: Int = defaultCooldownSeconds) {
        lock.withLock {
            cooldownUntil = LocalDateTime.now().plusSeconds(cooldownSeconds.toLong())
            message = reason
            logger.warn { "$serviceName entering cooldown for ${cooldownSeconds}s: $reason" }
        }
    }

    /**
     * Clear cooldown status.
     */
    fun clearCooldown() {
        lock.withLock {
            if (cooldownUntil != null) {
                logger.info { "$serviceName cooldown cleared" }
            }
            cooldownUntil = null
            message = null
        }
    }

    /**
     * Get remaining cooldown time in seconds.
     */
    fun getRemainingCooldown(): Int {
        lock.withLock {
            val until = cooldownUntil ?: return 0
            val remaining = java.time.Duration.between(LocalDateTime.now(), until).seconds
            return maxOf(0, remaining.toInt())
        }
    }

    /**
     * Get current status as a map.
     */
    fun getStatus(): Map<String, Any?> = lock.withLock {
        val available = isAvailable()
        mapOf(
            "available" to available,
            "cooldownUntil" to cooldownUntil?.toString(),
            "message" to if (!available) message else null
        )
    }
}

/**
 * Global rate limit manager for API services.
 */
@Component
class RateLimitManager {

    val gemini = RateLimitStatus("Gemini", 60)
    val alphaVantage = RateLimitStatus("Alpha Vantage", 60)

    private val services = mapOf(
        "gemini" to gemini,
        "alpha_vantage" to alphaVantage
    )

    /**
     * Get status of all services.
     */
    fun getAllStatus(): Map<String, Map<String, Any?>> {
        return services.mapValues { (_, status) -> status.getStatus() }
    }

    /**
     * Get a specific service's status.
     */
    fun getService(name: String): RateLimitStatus? = services[name]
}
