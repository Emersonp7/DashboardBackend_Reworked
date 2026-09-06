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
├── Application.java        # Spring Boot entry point
├── models/                 # Data models (POJOs), grouped per integration
│   ├── SpotifyModels/
│   ├── GoogleCalendarModels/
│   └── NewsModels/
├── service/                 # Business logic layer, one service per integration
│   ├── SpotifyService.java
│   ├── GoogleCalendarService.java
│   └── NewsService.java
└── routers/                 # REST controllers (endpoint definitions) — mirrors the
                              # original FastAPI routers; not yet implemented
```

`models/` and `service/` are subdivided per external integration (e.g. `SpotifyModels`) to keep each source's data shapes and logic isolated, matching the pattern already used for Spotify.

## Current state

Each service (`SpotifyService`, `GoogleCalendarService`, `NewsService`) implements `CommandLineRunner` and currently exercises its own logic directly on application startup for local testing/iteration — none of them are wired to HTTP endpoints yet. `routers/` is scaffolded but empty; adding controllers there (returning the typed models from each service) is the next step toward parity with the original FastAPI backend.

### Integrations

- **Spotify** (`SpotifyService`) — OAuth2 authorization code flow (with refresh), currently-playing track lookup. Credentials are cached to disk (`src/main/resources/Credentials/cached.json`) and refreshed automatically when expired.
- **Google Calendar** (`GoogleCalendarService`) — fetches upcoming calendar events (`EventModel`).
- **News** (`NewsService`) — aggregates from multiple sources (Hacker News API, Telemundo) into per-source models (`HackerNewsModel`, `TelemundoModel`).

## Configuration

Secrets (Spotify client ID/secret, redirect URI, etc.) are injected via environment variables referenced in `application.properties` (e.g. `${SPOTIFYCLIENTID}`), not committed to the repo. `gradleBootRunWithEnv.sh` runs `./gradlew bootRun` with the required environment variables set.

## Running

```
./gradlew bootRun
```
