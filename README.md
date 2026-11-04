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
│   ├── SpotifyModels/         # TrackModel, ArtistsModel, DeviceModel, PlaybackStatusModel, SpotifyCredentialsModel
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
| GET | `/health` | Liveness check, returns `{"status": "ok", "service": "spotify"}` |
| GET | `/authorize` | Returns the Spotify OAuth authorization URL |
| GET | `/callback?code=` | Exchanges an auth code for tokens and persists them |
| POST | `/skip` | Skips to the next track |
| POST | `/skip/{trackId}` | Skips forward through the queue to the given track and returns its `TrackModel` (empty response if the track isn't queued) |
| POST | `/previous` | Skips to the previous track |
| GET | `/current-track` | Returns the currently playing `TrackModel` (empty model if nothing is playing) |
| GET | `/queue` | Returns the upcoming queue as `List<TrackModel>` |
| GET | `/toggle-playback` | Plays or pauses playback and returns a `PlaybackStatusModel` |
| GET | `/active-device` | Returns the active `DeviceModel` (empty response if no device is active) |

`/toggle-playback` reports the action taken (`Play`, `Pause`, or `None`) and its status (`Ok` or `Fail`). If no device is active, playback is transferred to the default device (`DEFAULTDEVICENAME` in `SpotifyService`) and started there.

Auth uses the OAuth2 authorization code flow (with refresh); credentials are cached to disk (`src/main/resources/Credentials/cached.json`) via `SpotifyCredentialStore` and refreshed automatically when expired.

**Google Calendar** — `/api/google` (`GoogleCalendarRouter`)
| Method | Path | Description |
| --- | --- | --- |
| GET | `/events?limit=` | Upcoming events from all of the user's calendars (`limit` optional, defaults to 10, applied per calendar) |
| GET | `/events/{date}` | Events on a specific `YYYY-MM-DD` date, from the primary calendar only |

Both return `List<EventModel>`, where `calendarType` labels each event's source (`Primary`, `Holidays`, `Canvas Assignments`, or `Misc.`). Auth uses Google's installed-app OAuth flow (`GoogleCalendarAuthService`), with the local callback receiver on port `8081` and tokens stored under `tokens/`.

**News** — `/api/news/...`
| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/news/hacker-news/recent-news?limit=` | Top Hacker News stories (`limit` optional, defaults to 5), via the Hacker News Firebase API |
| GET | `/api/news/telemundo/recent-news?limit=` | Recent Telemundo headlines (`limit` optional, defaults to 5), scraped from telemundo.com |

Both return their respective typed models (`HackerNewsModel`, `TelemundoModel`).

## Configuration

Spotify secrets (`SPOTIFYCLIENTID`, `SPOTIFYCLIENTSECRET`, `REDIRECTURI`) are injected via environment variables referenced in `application.properties`, not committed to the repo. `gradleBootRunWithEnv.sh` loads them from a local `.env` file and runs `./gradlew bootRun`. The Google OAuth client JSON is read from the classpath at `src/main/resources/Credentials/googleCredentials.json`. The server listens on `127.0.0.1` (see `server.address` in `application.properties`).

## Running

```
./gradleBootRunWithEnv.sh
```
