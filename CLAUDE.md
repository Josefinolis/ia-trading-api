# IA Trading API - Kotlin/Spring Boot

Stock sentiment analysis API using Kotlin, Spring Boot 3, and Gemini AI.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Spring Boot 3.2                          │
├─────────────┬─────────────┬─────────────┬──────────────────┤
│ Controllers │  Services   │   Clients   │   Schedulers     │
│  (REST API) │  (Business) │ (External)  │  (Background)    │
├─────────────┴─────────────┴─────────────┴──────────────────┤
│                  Spring Data JPA                            │
├─────────────────────────────────────────────────────────────┤
│                    PostgreSQL                               │
└─────────────────────────────────────────────────────────────┘
```

## Tech Stack

- **Language**: Kotlin 1.9
- **Framework**: Spring Boot 3.2
- **JVM**: Java 21 with Virtual Threads
- **Database**: PostgreSQL 16 / H2 (dev)
- **Build**: Gradle Kotlin DSL

## Key Modules

| Module | Purpose |
|--------|---------|
| `controller/` | REST API endpoints |
| `service/` | Business logic |
| `client/` | External API integrations |
| `scheduler/` | Background jobs |
| `entity/` | JPA entities |
| `repository/` | Spring Data repositories |

## Configuration

```yaml
# application.yml profiles
spring:
  profiles:
    active: dev|production

# Environment variables
ALPHA_VANTAGE_API_KEY=xxx
GEMINI_API_KEY=xxx
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/ia_trading
SPRING_DATASOURCE_USERNAME=ia_trading
SPRING_DATASOURCE_PASSWORD=xxx
SCHEDULER_ENABLED=false
```

## Commands

```bash
# Development (H2)
./gradlew bootRun

# Production build
./gradlew build

# Docker
docker build -t ia-trading-api .
docker-compose up

# Tests
./gradlew test
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/tickers | List all tickers |
| POST | /api/tickers | Add ticker |
| DELETE | /api/tickers/{ticker} | Remove ticker |
| GET | /api/tickers/{ticker}/news | Get news |
| GET | /api/tickers/{ticker}/sentiment | Get sentiment |
| POST | /api/tickers/{ticker}/fetch | Trigger news fetch |
| POST | /api/tickers/{ticker}/analyze | Trigger analysis |
| GET | /health | Health check |
| GET | /api/status | Rate limit status |

## Production

- **URL**: http://195.20.235.94
- **Deploy**: Push to `main` triggers GitHub Actions
- **Infrastructure**: https://github.com/Josefinolis/documentation

## Why Kotlin?

Migrated from Python/FastAPI due to concurrency issues:
- Python GIL blocked event loop during scraping
- Kotlin with Virtual Threads provides true parallelism
- JVM has better tooling for debugging concurrency
- Same language as the Android mobile app

## Memory Configuration

```
JVM: -Xms256m -Xmx384m
Container: 512MB limit, 768MB swap
```
