# DashboardBackend.Reworked

A Java/Spring Boot rewrite of a previous Python (FastAPI) personal dashboard backend. The original aggregated data from a handful of third-party services (Spotify, Google Calendar, news sources) behind ad-hoc FastAPI routers with mostly untyped dict responses.

This rework's goal is a **more structured backend**: typed models for every external integration and **consistent response shapes across every endpoint**, so the frontend can rely on a stable contract instead of parsing loosely-shaped JSON per-source.

## Stack

- Java 21, Spring Boot 4.1.0, Gradle
- Jackson (`jackson-databind`) for JSON (de)serialization
- Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`) on all model classes
- Per-integration SDKs: `spotify-web-api-java` (Spotify), Google API Java client + `google-api-services-calendar` (Calendar)

## Project layout

```
src/main/java/org/dashboardbackendreworked/
├── Application.java         # Spring Boot entry point
├── configs/
│   └── CorsConfig.java       # CORS setup for the frontend dev server
├── models/                   # Data models (POJOs), grouped per integration
│   ├── SpotifyModels/         # TrackModel, ArtistsModel, DeviceModel, SpotifyCredentialsModel
│   ├── GoogleCalendarModels/  # EventModel
│   └── NewsModels/            # HackerNewsModel, HackerNewsItem, TelemundoModel
├── service/                  # Business logic layer, one (sub)package per integration
│   ├── SpotifyServices/       # SpotifyAuthService, SpotifyService, SpotifyCredentialStore
│   ├── GoogleCalendarServices/# GoogleCalendarAuthService, GoogleCalendarService
│   └── NewsServices/          # HackerNewsService, TelemundoNewsService
├── routers/                  # REST controllers, one (sub)package per integration
│   ├── SpotifyRouters/        # SpotifyRouter
│   ├── GoogleCalendarRouters/ # GoogleCalendarRouter
│   └── NewsRouters/           # HackerNewsRouter, TelemundoNewsRouter
└── utils/
    └── HttpResponseMethods.java  # Shared HTTP fetch/JSON helpers used by the News services
```

`models/`, `service/`, and `routers/` are all subdivided per external integration (e.g. `SpotifyModels`, `SpotifyServices`, `SpotifyRouters`) to keep each source's data shapes, logic, and endpoints isolated.

## Current state

All three integrations are wired to HTTP endpoints via Spring `@RestController` routers under `/api`.

### API endpoints

**Spotify** — `/api/spotify` (`SpotifyRouter`)
| Method | Path | Description |
| --- | --- | --- |
| GET | `/health` | Liveness check |
| GET | `/authorize` | Returns the Spotify OAuth authorization URL |
| GET | `/callback?code=` | Exchanges an auth code for tokens and persists them |
| POST | `/skip` | Skips to the next track |
| GET | `/current-track` | Returns the currently playing `TrackModel` |
| GET | `/queue` | Returns the upcoming queue as `List<TrackModel>` |

Auth uses the OAuth2 authorization code flow (with refresh); credentials are cached to disk (`src/main/resources/Credentials/cached.json`) via `SpotifyCredentialStore` and refreshed automatically when expired.

**Google Calendar** — `/api/google` (`GoogleCalendarRouter`)
| Method | Path | Description |
| --- | --- | --- |
| GET | `/events?limit=` | Upcoming events from the primary calendar (`limit` optional, defaults to 10) |
| GET | `/events/{date}` | Events on a specific `YYYY-MM-DD` date |

Both return `List<EventModel>`. Auth uses Google's installed-app OAuth flow (`GoogleCalendarAuthService`), with tokens stored under `tokens/`.

**News** — `/api/news/...`
| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/news/hacker-news/recent-news?limit=` | Top Hacker News stories (`limit` optional, defaults to 5), via the Hacker News Firebase API |
| GET | `/api/news/telemundo/recent-news?limit=` | Recent Telemundo headlines (`limit` optional, defaults to 5), scraped from telemundo.com |

Both return their respective typed models (`HackerNewsModel`, `TelemundoModel`).

## Configuration

Secrets (Spotify client ID/secret, redirect URI, Google OAuth client JSON, etc.) are injected via environment variables referenced in `application.properties` (e.g. `${SPOTIFYCLIENTID}`), not committed to the repo. `gradleBootRunWithEnv.sh` runs `./gradlew bootRun` with the required environment variables set. The server listens on `127.0.0.1` (see `server.address` in `application.properties`).

## Running

```
./gradlew bootRun
```
