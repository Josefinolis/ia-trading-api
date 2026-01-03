package com.iatrading.exception

import com.iatrading.dto.ErrorResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException::class, TickerNotFoundException::class)
    fun handleNotFound(ex: ApiException): ResponseEntity<ErrorResponse> {
        logger.warn { "Not found: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(detail = ex.message ?: "Resource not found", code = ex.code))
    }

    @ExceptionHandler(DuplicateException::class)
    fun handleDuplicate(ex: DuplicateException): ResponseEntity<ErrorResponse> {
        logger.warn { "Duplicate: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(detail = ex.message ?: "Resource already exists", code = ex.code))
    }

    @ExceptionHandler(RateLimitException::class)
    fun handleRateLimit(ex: RateLimitException): ResponseEntity<ErrorResponse> {
        logger.warn { "Rate limit exceeded for ${ex.service}" }
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse(detail = ex.message ?: "Rate limit exceeded", code = ex.code))
    }

    @ExceptionHandler(JobAlreadyRunningException::class)
    fun handleJobAlreadyRunning(ex: JobAlreadyRunningException): ResponseEntity<ErrorResponse> {
        logger.info { "Job already running: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(detail = ex.message ?: "Job already running", code = ex.code))
    }

    @ExceptionHandler(ExternalApiException::class)
    fun handleExternalApiError(ex: ExternalApiException): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "External API error from ${ex.service}: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(detail = ex.message ?: "External API error", code = ex.code))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn { "Validation error: $errors" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(detail = errors, code = "VALIDATION_ERROR"))
    }

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "API error: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(detail = ex.message ?: "Internal error", code = ex.code))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error(ex) { "Unexpected error: ${ex.message}" }
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(detail = "An unexpected error occurred", code = "INTERNAL_ERROR"))
    }
}
