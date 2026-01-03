package com.iatrading.client

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.iatrading.config.AppProperties
import com.iatrading.dto.NewsItemDto
import com.iatrading.exception.ExternalApiException
import mu.KotlinLogging
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Reddit API response models.
 */
data class RedditAccessTokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Int
)

data class RedditSearchResponse(
    val data: RedditSearchData
)

data class RedditSearchData(
    val children: List<RedditPostWrapper>
)

data class RedditPostWrapper(
    val data: RedditPost
)

data class RedditPost(
    val id: String,
    val title: String,
    val selftext: String?,
    val subreddit: String,
    val author: String?,
    val score: Int,
    val permalink: String,
    @SerializedName("created_utc")
    val createdUtc: Double,
    @SerializedName("link_flair_text")
    val linkFlairText: String?
)

/**
 * Client for Reddit API.
 */
@Component
class RedditClient(
    private val appProperties: AppProperties
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    // Meme flairs to filter out
    private val memeFlairs = listOf("meme", "shitpost", "yolo", "gain", "loss", "daily discussion")

    /**
     * Check if Reddit API is available (credentials configured).
     */
    fun isAvailable(): Boolean {
        return appProperties.reddit.clientId.isNotBlank() &&
                appProperties.reddit.clientSecret.isNotBlank()
    }

    /**
     * Fetch posts mentioning a ticker from configured subreddits.
     */
    fun fetchNews(
        ticker: String,
        timeFrom: LocalDateTime,
        timeTo: LocalDateTime,
        limitPerSubreddit: Int = 25
    ): List<NewsItemDto> {
        if (!isAvailable()) {
            logger.warn { "Reddit credentials not configured, skipping" }
            return emptyList()
        }

        val newsItems = mutableListOf<NewsItemDto>()
        val subreddits = appProperties.reddit.getSubredditList()
        val searchTerms = listOf("$$ticker", ticker)

        val timeFromUtc = timeFrom.toInstant(ZoneOffset.UTC)
        val timeToUtc = timeTo.toInstant(ZoneOffset.UTC)

        for (subreddit in subreddits) {
            for (searchTerm in searchTerms) {
                try {
                    val posts = searchSubreddit(subreddit, searchTerm, limitPerSubreddit)

                    for (post in posts) {
                        try {
                            val postTime = Instant.ofEpochSecond(post.createdUtc.toLong())

                            // Filter by time range
                            if (postTime.isBefore(timeFromUtc) || postTime.isAfter(timeToUtc)) {
                                continue
                            }

                            // Filter by score
                            if (post.score < appProperties.reddit.minScore) {
                                continue
                            }

                            // Filter out meme posts
                            if (isMemePost(post)) {
                                continue
                            }

                            val newsItem = postToNewsItem(post, ticker)

                            // Dedup by URL
                            if (newsItems.none { it.url == newsItem.url }) {
                                newsItems.add(newsItem)
                            }
                        } catch (e: Exception) {
                            logger.debug { "Error processing Reddit post: ${e.message}" }
                        }
                    }
                } catch (e: Exception) {
                    logger.warn { "Error searching r/$subreddit for '$searchTerm': ${e.message}" }
                }
            }
        }

        logger.info { "Retrieved ${newsItems.size} posts from Reddit for $ticker" }
        return newsItems
    }

    private fun searchSubreddit(subreddit: String, query: String, limit: Int): List<RedditPost> {
        val token = getAccessToken()

        val url = "https://oauth.reddit.com/r/$subreddit/search" +
                "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&sort=relevance&t=month&limit=$limit&restrict_sr=true"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", appProperties.reddit.userAgent)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ExternalApiException("Reddit", "HTTP ${response.code}")
            }

            val body = response.body?.string() ?: return emptyList()
            val searchResponse = gson.fromJson(body, RedditSearchResponse::class.java)

            return searchResponse.data.children.map { it.data }
        }
    }

    private fun getAccessToken(): String {
        // Return cached token if still valid
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return accessToken!!
        }

        logger.debug { "Fetching new Reddit access token" }

        val credential = Credentials.basic(
            appProperties.reddit.clientId,
            appProperties.reddit.clientSecret
        )

        val formBody = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build()

        val request = Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .header("Authorization", credential)
            .header("User-Agent", appProperties.reddit.userAgent)
            .post(formBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ExternalApiException("Reddit", "Failed to get access token: HTTP ${response.code}")
            }

            val body = response.body?.string()
                ?: throw ExternalApiException("Reddit", "Empty token response")

            val tokenResponse = gson.fromJson(body, RedditAccessTokenResponse::class.java)

            accessToken = tokenResponse.accessToken
            tokenExpiry = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000) - 60000 // 1 min buffer

            return tokenResponse.accessToken
        }
    }

    private fun isMemePost(post: RedditPost): Boolean {
        val flair = post.linkFlairText?.lowercase() ?: return false
        return memeFlairs.any { it in flair }
    }

    private fun postToNewsItem(post: RedditPost, ticker: String): NewsItemDto {
        val title = post.title.take(500)

        var summary = post.selftext?.take(2000) ?: ""
        if (summary.isBlank()) {
            summary = "Reddit post discussing $ticker: $title"
        }

        val publishedDate = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(post.createdUtc.toLong()),
            ZoneOffset.UTC
        ).format(dateFormatter)

        // Calculate relevance score from Reddit score
        val maxScore = 10000.0
        val relevanceScore = minOf(post.score.toDouble() / maxScore, 1.0)

        return NewsItemDto(
            title = title,
            summary = summary,
            publishedDate = publishedDate,
            source = "r/${post.subreddit}",
            sourceType = "reddit",
            url = "https://reddit.com${post.permalink}",
            relevanceScore = relevanceScore,
            engagementScore = post.score,
            author = post.author
        )
    }
}
