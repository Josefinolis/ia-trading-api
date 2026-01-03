package com.iatrading.client

import com.google.gson.Gson
import com.iatrading.config.AppProperties
import com.iatrading.dto.GeminiResponse
import com.iatrading.dto.SentimentAnalysisResult
import com.iatrading.exception.AnalysisException
import com.iatrading.exception.RateLimitException
import com.iatrading.util.RateLimitManager
import mu.KotlinLogging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Request/Response models for Gemini API.
 */
data class GeminiApiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val responseMimeType: String = "application/json"
)

data class GeminiApiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiCandidateContent?
)

data class GeminiCandidateContent(
    val parts: List<GeminiCandidatePart>?
)

data class GeminiCandidatePart(
    val text: String?
)

/**
 * Client for Google Gemini AI API for sentiment analysis.
 */
@Component
class GeminiClient(
    private val appProperties: AppProperties,
    private val rateLimitManager: RateLimitManager
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Check if the Gemini API is available.
     */
    fun isAvailable(): Boolean {
        if (appProperties.gemini.apiKey.isBlank()) {
            logger.warn { "Gemini API key not configured" }
            return false
        }
        return rateLimitManager.gemini.isAvailable()
    }

    /**
     * Analyze sentiment of news text using Gemini AI.
     */
    fun analyzeSentiment(ticker: String, newsText: String): SentimentAnalysisResult? {
        if (!isAvailable()) {
            val remaining = rateLimitManager.gemini.getRemainingCooldown()
            if (remaining > 0) {
                logger.warn { "Gemini API in cooldown, ${remaining}s remaining" }
                return null
            }
            logger.warn { "Gemini API not available" }
            return null
        }

        val prompt = buildPrompt(ticker, newsText)

        try {
            logger.debug { "Calling Gemini API for sentiment analysis of $ticker" }

            val request = GeminiApiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    responseMimeType = "application/json"
                )
            )

            val requestBody = gson.toJson(request).toRequestBody(jsonMediaType)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${appProperties.gemini.model}:generateContent?key=${appProperties.gemini.apiKey}"

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""

                    // Check for rate limit
                    if (response.code == 429 ||
                        "quota" in errorBody.lowercase() ||
                        "rate limit" in errorBody.lowercase()) {
                        rateLimitManager.gemini.enterCooldown("Rate limit exceeded", 60)
                        logger.error { "Gemini rate limit exceeded" }
                        return null
                    }

                    logger.error { "Gemini API error: HTTP ${response.code} - $errorBody" }
                    throw AnalysisException("Gemini API error: HTTP ${response.code}")
                }

                val body = response.body?.string()
                    ?: throw AnalysisException("Empty response from Gemini")

                val apiResponse = gson.fromJson(body, GeminiApiResponse::class.java)

                val resultText = apiResponse.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?: throw AnalysisException("No content in Gemini response")

                // Parse the JSON response
                val geminiResult = gson.fromJson(resultText, GeminiResponse::class.java)

                logger.info { "Sentiment analysis for $ticker: ${geminiResult.SENTIMENT}" }

                return geminiResult.toSentimentAnalysisResult()
            }

        } catch (e: RateLimitException) {
            return null
        } catch (e: AnalysisException) {
            throw e
        } catch (e: Exception) {
            val errorStr = e.message ?: ""
            if ("429" in errorStr || "quota" in errorStr.lowercase() || "rate limit" in errorStr.lowercase()) {
                rateLimitManager.gemini.enterCooldown("Rate limit exceeded", 60)
                logger.error { "Gemini rate limit exceeded: ${e.message}" }
                return null
            }
            logger.error(e) { "Error calling Gemini API" }
            throw AnalysisException("Gemini API error: ${e.message}", e)
        }
    }

    private fun buildPrompt(ticker: String, newsText: String): String {
        return """Act as a quantitative market analyst specialized in short-term trading.
Evaluate the news text provided about the stock $ticker to classify its sentiment and potential short-term price impact.

Format the response strictly as a JSON object with two fields:
1. "SENTIMENT" (Use only one of these categories: 'Highly Negative', 'Negative', 'Neutral', 'Positive', 'Highly Positive').
2. "JUSTIFICATION" (A concise 1-2 sentence summary explaining the main reason for the impact).

TEXT TO ANALYZE:
---
$newsText
---"""
    }
}
