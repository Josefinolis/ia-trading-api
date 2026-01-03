package com.iatrading.dto

/**
 * DTO for sentiment analysis result from Gemini AI.
 */
data class SentimentAnalysisResult(
    val sentiment: String,
    val justification: String
) {
    companion object {
        // Valid sentiment categories
        val VALID_SENTIMENTS = listOf(
            "Highly Negative",
            "Negative",
            "Neutral",
            "Positive",
            "Highly Positive"
        )

        fun isValidSentiment(sentiment: String): Boolean =
            VALID_SENTIMENTS.any { it.equals(sentiment, ignoreCase = true) }
    }
}

/**
 * DTO for Gemini API response.
 */
data class GeminiResponse(
    val SENTIMENT: String,
    val JUSTIFICATION: String
) {
    fun toSentimentAnalysisResult(): SentimentAnalysisResult {
        return SentimentAnalysisResult(
            sentiment = SENTIMENT,
            justification = JUSTIFICATION
        )
    }
}
