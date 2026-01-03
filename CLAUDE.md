# IA Trading API - Kotlin/Spring Boot

Stock sentiment analysis API using Kotlin, Spring Boot 3, and Gemini AI.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot 3.2                             │
├──────────────┬──────────────┬──────────────┬───────────────────┤
│  Controllers │   Services   │   Clients    │    Schedulers     │
│  (REST API)  │  (Business)  │  (External)  │   (Background)    │
├──────────────┴──────────────┴──────────────┴───────────────────┤
│                      Spring Data JPA                             │
├─────────────────────────────────────────────────────────────────┤
│                        PostgreSQL                                │
└─────────────────────────────────────────────────────────────────┘
```

## Tech Stack

- **Language**: Kotlin 1.9
- **Framework**: Spring Boot 3.2
- **JVM**: Java 21 with Virtual Threads
- **Database**: PostgreSQL 16 / H2 (dev)
- **Build**: Gradle Kotlin DSL
- **AI**: Google Gemini (gemini-2.5-flash-lite)

## Project Structure

```
src/main/kotlin/com/iatrading/
├── IaTradingApiApplication.kt
├── config/
│   ├── AppConfig.kt          # AppProperties configuration
│   ├── CacheConfig.kt        # Caffeine caching
│   └── WebConfig.kt          # CORS, Jackson snake_case
├── controller/
│   ├── HealthController.kt   # /health, /api/status
│   ├── TickerController.kt   # /api/tickers/*
│   └── JobController.kt      # /api/jobs/*
├── service/
│   ├── WatchlistService.kt   # Ticker CRUD
│   ├── NewsService.kt        # News management
│   └── SentimentService.kt   # Sentiment aggregation
├── client/
│   ├── AlphaVantageClient.kt # Financial news API
│   ├── RedditClient.kt       # Reddit OAuth API
│   ├── GeminiClient.kt       # AI sentiment analysis
│   └── NewsAggregator.kt     # Multi-source aggregation
├── scheduler/
│   ├── NewsFetcherScheduler.kt
│   └── AnalyzerScheduler.kt
├── entity/
│   ├── WatchlistTicker.kt
│   ├── NewsRecord.kt
│   └── TickerSentiment.kt
├── repository/
│   ├── WatchlistTickerRepository.kt
│   ├── NewsRecordRepository.kt
│   └── TickerSentimentRepository.kt
├── dto/
│   ├── TickerDto.kt
│   ├── NewsDto.kt
│   ├── SentimentDto.kt
│   └── CommonDto.kt
├── exception/
│   ├── Exceptions.kt
│   └── GlobalExceptionHandler.kt
└── util/
    ├── RateLimitManager.kt   # API rate limiting
    └── JobTracker.kt         # Background job tracking
```

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `ALPHA_VANTAGE_API_KEY` | Yes | - | Alpha Vantage API key |
| `GEMINI_API_KEY` | Yes | - | Google Gemini API key |
| `REDDIT_CLIENT_ID` | No | - | Reddit app client ID |
| `REDDIT_CLIENT_SECRET` | No | - | Reddit app client secret |
| `SCHEDULER_ENABLED` | No | false | Enable background jobs |
| `SPRING_DATASOURCE_URL` | No | H2 | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | No | postgres | DB username |
| `SPRING_DATASOURCE_PASSWORD` | No | postgres | DB password |

### application.yml Profiles

- `dev` - H2 in-memory database, debug logging
- `prod` - PostgreSQL, minimal logging

## Commands

```bash
# Development (H2 database)
./gradlew bootRun

# Production build
./gradlew bootJar

# Run tests
./gradlew test

# Docker build
docker build -t ia-trading-api .
```

## API Endpoints

### Tickers

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/tickers` | List all tickers with sentiment |
| POST | `/api/tickers` | Add ticker (body: `{"ticker": "AAPL", "name": "Apple"}`) |
| GET | `/api/tickers/{ticker}` | Get ticker details |
| DELETE | `/api/tickers/{ticker}` | Remove ticker |
| GET | `/api/tickers/{ticker}/news` | Get news (query: `status`, `limit`, `offset`) |
| GET | `/api/tickers/{ticker}/sentiment` | Get aggregated sentiment |
| POST | `/api/tickers/{ticker}/fetch` | Trigger news fetch (query: `hours`) |
| POST | `/api/tickers/{ticker}/analyze` | Trigger AI analysis |

### Jobs

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/jobs/fetch-news` | Fetch news for all tickers |
| POST | `/api/jobs/analyze` | Analyze all pending news |
| GET | `/api/jobs/status` | Get job status |

### Health

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Basic health check |
| GET | `/api/status` | API rate limit status |

## News Sources

| Source | Type | Rate Limit | Notes |
|--------|------|------------|-------|
| Alpha Vantage | Financial News | 5/min | Primary news source |
| Reddit | Social Sentiment | OAuth | Subreddits: wallstreetbets, stocks, investing |

## Sentiment Scoring

### Categories
| Category | Score |
|----------|-------|
| Highly Positive | +1.0 |
| Positive | +0.5 |
| Neutral | 0.0 |
| Negative | -0.5 |
| Highly Negative | -1.0 |

### Trading Signals
| Normalized Score | Signal |
|------------------|--------|
| >= 0.5 | STRONG BUY |
| >= 0.2 | BUY |
| >= -0.2 | HOLD |
| >= -0.5 | SELL |
| < -0.5 | STRONG SELL |

## Rate Limiting

| Service | Limit | Auto-Cooldown |
|---------|-------|---------------|
| Alpha Vantage | 5 req/min | 60s |
| Gemini | 15 req/min | 60s |

## Production

- **URL**: http://195.20.235.94
- **Port**: 80 (mapped to container 8080)
- **Deploy**: Push to `main` triggers GitHub Actions
- **Container**: `ghcr.io/josefinolis/ia-trading-api:latest`

## Memory Configuration

```
JVM: -Xms256m -Xmx384m
Container: 512MB limit, 768MB swap
GC: ZGC Generational
```

## Migration from Python

This API was migrated from Python/FastAPI due to GIL blocking issues:

| Aspect | Python | Kotlin |
|--------|--------|--------|
| Concurrency | ThreadPoolExecutor (GIL limited) | Virtual Threads (true parallelism) |
| Framework | FastAPI | Spring Boot 3.2 |
| Database | SQLAlchemy | Spring Data JPA |
| HTTP Client | requests/aiohttp | OkHttp/Retrofit |
| Scheduling | APScheduler | Spring @Scheduled |
