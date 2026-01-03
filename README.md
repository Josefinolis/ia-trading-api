# IA Trading API (Kotlin/Spring Boot)

Market Sentiment Analysis API - Analyze financial news sentiment using AI.

This is a Kotlin/Spring Boot migration of the Python/FastAPI backend.

## Tech Stack

- **Kotlin 1.9** - Primary language
- **Spring Boot 3.2** - Web framework
- **Spring Data JPA** - Database access
- **PostgreSQL** - Production database
- **H2** - Development/testing database
- **Java 21** - With Virtual Threads for concurrency
- **Retrofit/OkHttp** - HTTP client for external APIs
- **Caffeine** - Caching

## Features

- REST API for ticker watchlist management
- News aggregation from multiple sources:
  - Alpha Vantage (financial news)
  - Reddit (social sentiment)
- AI-powered sentiment analysis via Google Gemini
- Background job scheduling for automated news fetching
- Rate limiting and cooldown management
- Docker support for production deployment

## Project Structure

```
src/main/kotlin/com/iatrading/
├── IaTradingApiApplication.kt    # Main application
├── config/                       # Configuration classes
├── controller/                   # REST controllers
├── dto/                          # Data Transfer Objects
├── entity/                       # JPA entities
├── repository/                   # Spring Data repositories
├── service/                      # Business logic
├── client/                       # External API clients
├── scheduler/                    # Background jobs
├── exception/                    # Custom exceptions
└── util/                         # Utilities
```

## API Endpoints

### Tickers

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tickers` | List all watched tickers with sentiment |
| POST | `/api/tickers` | Add a new ticker |
| DELETE | `/api/tickers/{ticker}` | Remove a ticker |
| GET | `/api/tickers/{ticker}` | Get ticker details |
| GET | `/api/tickers/{ticker}/news` | Get news for ticker |
| GET | `/api/tickers/{ticker}/sentiment` | Get aggregated sentiment |
| POST | `/api/tickers/{ticker}/fetch` | Trigger news fetch |
| POST | `/api/tickers/{ticker}/analyze` | Trigger analysis |

### Jobs

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/jobs/fetch-news` | Fetch news for all tickers |
| POST | `/api/jobs/fetch-news/{symbol}` | Fetch news for specific ticker |
| POST | `/api/jobs/analyze` | Analyze all pending news |
| GET | `/api/jobs/status` | Get job status |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Lightweight health check |
| GET | `/health/full` | Full health check with DB |
| GET | `/api/status` | API rate limit status |

## Development

### Prerequisites

- Java 21+
- Gradle 8.5+ (or use wrapper)

### Running Locally

1. Copy `.env.example` to `.env` and configure API keys:
   ```bash
   cp .env.example .env
   ```

2. Run with Gradle:
   ```bash
   ./gradlew bootRun
   ```

3. Access the API at http://localhost:8080
4. Swagger UI at http://localhost:8080/swagger-ui.html

### Running Tests

```bash
./gradlew test
```

### Building JAR

```bash
./gradlew bootJar
```

## Docker Deployment

### Development (H2 database)

```bash
docker-compose -f docker-compose.dev.yml up --build
```

### Production (PostgreSQL)

1. Configure `.env` with API keys
2. Run:
   ```bash
   docker-compose up --build
   ```

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `ALPHA_VANTAGE_API_KEY` | Yes | - | Alpha Vantage API key |
| `GEMINI_API_KEY` | Yes | - | Google Gemini API key |
| `REDDIT_CLIENT_ID` | No | - | Reddit app client ID |
| `REDDIT_CLIENT_SECRET` | No | - | Reddit app client secret |
| `TWITTER_ENABLED` | No | false | Enable Twitter scraping |
| `SCHEDULER_ENABLED` | No | false | Enable background scheduler |
| `DATABASE_URL` | No | H2 | PostgreSQL JDBC URL |

### Rate Limits

| Service | Limit | Cooldown |
|---------|-------|----------|
| Alpha Vantage | 5/min | 60s auto |
| Gemini | 15/min | 60s auto |

## Trading Signals

| Score Range | Signal |
|-------------|--------|
| >= 0.5 | STRONG BUY |
| >= 0.2 | BUY |
| >= -0.2 | HOLD |
| >= -0.5 | SELL |
| < -0.5 | STRONG SELL |

## Migration Notes

This project is a migration from Python/FastAPI. Key differences:

1. **Concurrency**: Uses Java 21 Virtual Threads instead of Python's ThreadPoolExecutor
2. **Database**: Spring Data JPA instead of SQLAlchemy
3. **Validation**: Jakarta Bean Validation instead of Pydantic
4. **HTTP Client**: OkHttp/Retrofit instead of requests
5. **Scheduling**: Spring @Scheduled instead of APScheduler

## License

MIT
