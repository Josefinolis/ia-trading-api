package com.iatrading.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(AppProperties::class)
class AppConfig

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val alphaVantage: AlphaVantageProperties = AlphaVantageProperties(),
    val gemini: GeminiProperties = GeminiProperties(),
    val reddit: RedditProperties = RedditProperties(),
    val twitter: TwitterProperties = TwitterProperties(),
    val scheduler: SchedulerProperties = SchedulerProperties()
)

data class AlphaVantageProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://www.alphavantage.co/query",
    val callsPerMinute: Int = 5
)

data class GeminiProperties(
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash-lite",
    val callsPerMinute: Int = 15
)

data class RedditProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val userAgent: String = "ia_trading/1.0",
    val subreddits: String = "wallstreetbets,stocks,investing,stockmarket,options",
    val minScore: Int = 10
) {
    fun getSubredditList(): List<String> = subreddits.split(",").map { it.trim() }
}

data class TwitterProperties(
    val enabled: Boolean = true,
    val minLikes: Int = 10,
    val minRetweets: Int = 5,
    val maxResults: Int = 50
)

data class SchedulerProperties(
    val enabled: Boolean = false,
    val newsFetchIntervalMinutes: Int = 30,
    val analysisIntervalMinutes: Int = 5
)
