# Explore Page -- Phase 2 User Stories

## Context

Phase 2 is a **data-layer phase** -- there is no new UI. The goal is to enrich every game in the library with richer IGDB metadata (themes, keywords, player perspectives, franchises, series, and promotional artwork) so that later phases (3-4) can build browsing and filtering UIs on top of this data.

The existing IGDB client (`server/internal/igdb/client.go`) fetches basic game metadata: name, summary, cover, screenshots, genres, involved companies, release date, rating, and game modes. The existing scraper (`server/internal/scraper/scraper.go`) calls this during `ScrapeGame` and stores the result in the `Game` model. This phase extends both the IGDB client and the scraper to fetch additional fields, introduces new DB models for the many-to-many relationships, adds a backfill admin endpoint, and exposes the new data through read-only API endpoints.

### Key constraints (from the plan's Decisions section)

- **All IGDB data is cached locally.** The Explore features must work when IGDB is unreachable. Stale data is acceptable; broken features are not.
- **IGDB rate limit: 4 requests/second.** The existing client already enforces this via a 250ms rate ticker. The backfill must respect this limit.
- **IGDB "collections" = game series.** The existing `GameCollection` model is user-created collections. IGDB collections (e.g., "Super Mario") are stored as `GameSeries` / `GameSeriesEntry` to avoid confusion.

### Naming conventions

| IGDB concept | Spela DB model | Rationale |
|---|---|---|
| Theme | `GameTheme` | Direct mapping |
| Keyword | `GameKeyword` | Direct mapping |
| Player Perspective | `GamePlayerPerspective` | Direct mapping |
| Franchise | `GameFranchise` | Direct mapping |
| Collection | `GameSeries` + `GameSeriesEntry` | Avoids collision with user `GameCollection` |
| Artwork | `GameArtworkImage` | Avoids collision with SteamGridDB `GameArtwork` |

---

## Story 1: Extend IGDB client to fetch enriched game data

**As a** developer implementing later Explore phases,
**I want** the IGDB client to fetch themes, keywords, player perspectives, franchises, collections (series), and artwork for a game,
**so that** this data can be stored locally and used for discovery features.

### Acceptance Criteria

#### New client method: `GetGameFull`
- A new method `GetGameFull(igdbID int)` is added to the IGDB client.
- It queries the IGDB `/games` endpoint for a single game by ID and requests the following fields (in addition to the fields already fetched by `SearchGame`):
  - `themes.name` -- e.g., "Sci-Fi", "Horror", "Fantasy"
  - `keywords.name` -- e.g., "time travel", "zombies", "procedural generation"
  - `player_perspectives.name` -- e.g., "First person", "Bird view", "Side view"
  - `franchises` (ID array) -- links to franchise entities
  - `collection` (single ID) -- the primary IGDB collection (series) the game belongs to
  - `artworks.image_id`, `artworks.width`, `artworks.height` -- promotional artwork
- It returns a struct containing all of the above in parsed form (not raw JSON).
- The method respects the existing rate limiter (waits on `rateLimiter` before each request).
- The method uses the same authentication flow as existing methods.
- If the game is not found (empty response), it returns `nil, nil`.

#### New client method: `GetCollection`
- A new method `GetCollection(collectionID int)` queries the IGDB `/collections` endpoint.
- It returns the collection name and the list of game IDs in the collection.
- It respects the rate limiter.

#### New client method: `GetFranchise`
- A new method `GetFranchise(franchiseID int)` queries the IGDB `/franchises` endpoint.
- It returns the franchise name and the list of game IDs in the franchise.
- It respects the rate limiter.

#### Error handling
- All new methods follow the same error wrapping convention as existing methods: `fmt.Errorf("doing thing: %w", err)`.
- Authentication failures, HTTP errors, and JSON decoding errors produce descriptive wrapped errors.
- IGDB returning an empty result set is not an error -- the method returns nil or empty slices as appropriate.

#### Tests
- Unit tests using an HTTP test server that returns canned IGDB JSON responses.
- Tests cover: successful fetch, empty result, HTTP error, malformed JSON.
- Tests verify rate limiter is consumed (the test should observe the expected delay or verify the channel was read).

---

## Story 2: New DB models for enriched metadata

**As a** developer,
**I want** new database models to store themes, keywords, player perspectives, franchises, series, and artwork images,
**so that** this data persists locally and can be queried efficiently.

### Acceptance Criteria

#### GameTheme
- Fields: `ID`, `CreatedAt`, `GameID` (indexed, not null), `IGDBThemeID` (int, not null), `Name` (string, not null).
- Unique constraint on `(game_id, igdb_theme_id)` to prevent duplicates.
- A game can have multiple themes (many-to-many via this join model).

#### GameKeyword
- Fields: `ID`, `CreatedAt`, `GameID` (indexed, not null), `IGDBKeywordID` (int, not null), `Name` (string, not null).
- Unique constraint on `(game_id, igdb_keyword_id)`.
- A game can have multiple keywords.

#### GamePlayerPerspective
- Fields: `ID`, `CreatedAt`, `GameID` (indexed, not null), `IGDBPerspectiveID` (int, not null), `Name` (string, not null).
- Unique constraint on `(game_id, igdb_perspective_id)`.
- A game can have multiple player perspectives.

#### GameFranchise
- Fields: `ID`, `CreatedAt`, `GameID` (indexed, not null), `IGDBFranchiseID` (int, not null), `FranchiseName` (string, not null).
- Unique constraint on `(game_id, igdb_franchise_id)`.
- A game can belong to multiple franchises.

#### GameSeries
- Fields: `ID`, `CreatedAt`, `UpdatedAt`, `IGDBCollectionID` (int, unique index, not null), `Name` (string, not null).
- Represents an IGDB collection (game series like "Super Mario", "The Legend of Zelda").

#### GameSeriesEntry
- Fields: `ID`, `CreatedAt`, `SeriesID` (foreign key to `GameSeries`, indexed, not null), `GameID` (foreign key to `Game`, nullable -- null means the game is in the series but not in the local library), `IGDBGameID` (int, not null).
- Unique constraint on `(series_id, igdb_game_id)`.
- `GameID` is nullable because a series may include IGDB games that are not in the local library. This is needed for Phase 4's "You own 8 of 15 games" display.

#### GameArtworkImage
- Fields: `ID`, `CreatedAt`, `GameID` (indexed, not null), `IGDBImageID` (string, not null), `Width` (int), `Height` (int).
- Unique constraint on `(game_id, igdb_image_id)`.
- Stores IGDB promotional artwork (distinct from SteamGridDB `GameArtwork` which stores hero/grid/logo/icon).
- The image URL is constructed at query time using the existing `igdb.ImageURL()` helper, not stored.

#### Auto-migration
- All new models are added to the GORM auto-migration list in `server/internal/db/database.go`.
- Migrating an existing database with no new tables creates them without errors.
- Migrating a database that already has these tables (idempotent re-run) completes without errors.

#### Tests
- Unit tests verify that records can be created, queried, and that unique constraints are enforced (duplicate insert returns an error or is handled via upsert).

---

## Story 3: Extend scraper to fetch and store enriched metadata during game scraping

**As a** server admin running a scrape,
**I want** the scraper to automatically fetch and store themes, keywords, perspectives, franchises, series membership, and artwork for each game,
**so that** my library is enriched without any extra manual steps.

### Acceptance Criteria

#### Scraper integration
- When `ScrapeGame` runs for a game that has an IGDB match (i.e., `ScraperID` starts with `igdb:`), it calls `GetGameFull` with the IGDB game ID after the initial search/match.
- This is a single additional IGDB API call per game (the enriched query fetches all new fields at once).
- The scraper stores the returned data in the new DB models (GameTheme, GameKeyword, GamePlayerPerspective, GameFranchise, GameSeries, GameSeriesEntry, GameArtworkImage).

#### Upsert behavior
- On re-scrape (`ScrapeAttempts > 0`), old enrichment data for the game is replaced:
  - Existing GameTheme, GameKeyword, GamePlayerPerspective, GameFranchise, GameArtworkImage rows for the game are deleted and re-created from the fresh IGDB response.
  - GameSeriesEntry rows are upserted (insert or update); stale entries for this game are removed.
- This ensures re-scraping never produces duplicate rows.

#### Series handling
- When a game belongs to an IGDB collection, the scraper:
  1. Checks if a `GameSeries` row with that `IGDBCollectionID` already exists.
  2. If not, calls `GetCollection` to fetch the series name and creates the `GameSeries` row.
  3. Creates a `GameSeriesEntry` linking the series to the local game.
  4. Does NOT eagerly fetch all other games in the series (this would be too many API calls). Series membership for non-library games is populated by the backfill endpoint (Story 4).

#### Franchise handling
- When a game has franchise IDs, the scraper stores a `GameFranchise` row for each one.
- The franchise name is fetched via `GetFranchise` for each unique franchise ID not already cached in the DB. If the name was already fetched for a different game, it is reused from the existing `GameFranchise` rows (query by `igdb_franchise_id`) to avoid redundant API calls.

#### Graceful degradation
- If `GetGameFull` fails (IGDB down, rate limit hit, network error), the scraper logs a warning and continues. The basic metadata (title, description, cover, screenshots, etc.) from the initial search is still saved.
- The game is not marked as failed. The enrichment data simply remains empty for this game until the next scrape or backfill.
- This matches the existing pattern where IGDB failures fall back to LibRetro data.

#### `ScrapeGameWithIGDBMatch` also enriches
- The admin "re-scrape with specific IGDB match" flow (`ScrapeGameWithIGDBMatch`) also calls the enrichment logic, so manually matched games get the same rich metadata.

#### Tests
- Unit tests with a mock IGDB HTTP server verify:
  - Themes, keywords, perspectives, franchises, and artwork are stored correctly after scraping.
  - Re-scraping replaces old enrichment data without duplicates.
  - IGDB enrichment failure does not prevent basic metadata from being saved.
  - Series is created on first encounter and reused on subsequent games in the same series.

---

## Story 4: Admin backfill endpoint for enriching existing games

**As a** server admin,
**I want** a backfill endpoint that enriches all existing games with the new IGDB metadata,
**so that** games scraped before this feature was added also get themes, keywords, franchises, etc.

### Acceptance Criteria

#### Endpoint: `POST /api/admin/enrich-metadata`
- Available only to admin and owner roles (behind `AdminMiddleware`).
- Accepts an optional query parameter `mode`:
  - `"missing"` (default): only enrich games that have an IGDB match (`ScraperID` starts with `igdb:`) but have zero `GameTheme` rows (i.e., never been enriched).
  - `"all"`: re-enrich every game that has an IGDB match, replacing existing enrichment data.
- Returns `202 Accepted` immediately with `{ "message": "enrichment started in background", "total": <count> }`.
- Returns `409 Conflict` if an enrichment or scrape operation is already in progress.

#### Execution behavior
- The backfill runs in a background goroutine (same pattern as `TriggerScrape`).
- It iterates over matching games and calls `GetGameFull` + stores results for each one.
- It respects the IGDB rate limiter (4 req/sec). For a library of 1000 games, the backfill takes ~4-5 minutes at minimum.
- It does NOT re-run the full scrape (no re-downloading of cover art, screenshots, etc.). It only fetches and stores the new enrichment fields.

#### Progress tracking
- Progress is broadcast via WebSocket events, following the same pattern as scrape progress:
  - `enrich_started` when the backfill begins.
  - `enrich_progress` with `{ current, total, gameName, successes, failures }` after each game.
  - `enrich_complete` with `{ enriched, total }` when finished.
  - `enrich_error` if the entire operation fails.
- A status endpoint `GET /api/admin/enrich-metadata/status` returns whether an enrichment is active and current progress (same pattern as `GET /api/admin/scrape/status`).

#### Error resilience
- If enrichment fails for an individual game (IGDB timeout, auth error, malformed response), the backfill logs a warning, increments the failure counter, and continues to the next game.
- The backfill does not abort on individual failures.
- If the IGDB client is not configured (no credentials), the endpoint returns `400 Bad Request` with `{ "error": "IGDB credentials not configured" }`.

#### Series population during backfill
- During backfill, when a `GameSeries` is encountered for the first time, the backfill also calls `GetCollection` to fetch the full list of game IDs in that series and populates `GameSeriesEntry` rows for all of them (with `GameID` null for games not in the local library).
- This is what powers the "You own 8 of 15 games" display in Phase 4.
- For series already fully populated (all `GameSeriesEntry` rows exist), the backfill skips the `GetCollection` call.

#### Tests
- Unit tests with mock IGDB responses verify:
  - The endpoint returns 202 and starts a background job.
  - The `"missing"` mode skips already-enriched games.
  - The `"all"` mode re-enriches all games.
  - Individual game failures do not abort the backfill.
  - The endpoint returns 409 if a job is already running.
  - The endpoint returns 400 if IGDB is not configured.
  - Progress events are broadcast via WebSocket.

---

## Story 5: API endpoint -- list themes with game counts

**As a** future Explore page UI (Phase 3),
**I want** an endpoint that lists all themes present in the library with the number of games per theme,
**so that** I can display theme browsing cards with meaningful counts.

### Acceptance Criteria

#### Endpoint: `GET /api/themes`
- Requires authentication (standard user or admin).
- Returns a JSON array of themes, each with:
  - `id` (string) -- the IGDB theme ID.
  - `name` (string) -- e.g., "Science fiction", "Horror", "Fantasy".
  - `gameCount` (int) -- number of games in the local library tagged with this theme.
- Themes are sorted by `gameCount` descending (most popular themes first).
- Themes with zero games in the library are excluded.
- Response example:
  ```json
  [
    { "id": "17", "name": "Fantasy", "gameCount": 42 },
    { "id": "18", "name": "Science fiction", "gameCount": 35 },
    { "id": "19", "name": "Horror", "gameCount": 12 }
  ]
  ```

#### Endpoint: `GET /api/themes/:id/games`
- Returns a paginated list of games tagged with the given theme.
- Uses the existing `GameResponse` format for game objects.
- Supports `?page=1&pageSize=20` query parameters (defaults: page 1, pageSize 20).
- Games are sorted by rating descending.
- Returns 404 if no theme with the given ID exists in the library.

#### Tests
- Unit tests verify correct game counts, sorting, pagination, and 404 for unknown theme.

---

## Story 6: API endpoint -- list keywords with game counts

**As a** future Explore page UI (Phase 3),
**I want** an endpoint that lists popular keywords in the library,
**so that** I can display keyword chips for browsing.

### Acceptance Criteria

#### Endpoint: `GET /api/keywords`
- Requires authentication.
- Returns a JSON array of keywords, each with `id`, `name`, `gameCount`.
- Sorted by `gameCount` descending.
- By default, returns the top 50 keywords (to avoid returning hundreds of obscure keywords).
- Supports `?limit=N` to override the default (max 200).
- Keywords with zero games are excluded.

#### Endpoint: `GET /api/keywords/:id/games`
- Returns a paginated list of games tagged with the given keyword.
- Uses `GameResponse` format, sorted by rating descending.
- Supports `?page=1&pageSize=20`.
- Returns 404 if the keyword is not found.

#### Tests
- Unit tests verify default limit of 50, custom limit, correct counts, pagination.

---

## Story 7: API endpoint -- list series (IGDB collections) in the library

**As a** future Franchise page UI (Phase 4),
**I want** an endpoint that lists all game series present in the library,
**so that** I can display franchise browsing cards.

### Acceptance Criteria

#### Endpoint: `GET /api/series`
- Requires authentication.
- Returns a JSON array of game series that have at least one game in the local library.
- Each entry includes:
  - `id` (string) -- the `GameSeries.ID`.
  - `igdbCollectionId` (int) -- the IGDB collection ID.
  - `name` (string) -- e.g., "Super Mario", "The Legend of Zelda".
  - `totalGames` (int) -- total number of games in the series (from `GameSeriesEntry` rows, including those not in the library).
  - `libraryGames` (int) -- number of series games that are in the local library.
- Sorted by `libraryGames` descending (series with the most local games first).

#### Endpoint: `GET /api/series/:id`
- Returns the series detail with all games.
- Response includes:
  - `id`, `name`, `igdbCollectionId`.
  - `games` -- array of all games in the series, each with:
    - `igdbGameId` (int).
    - `name` (string) -- the IGDB game name (fetched during series population).
    - `inLibrary` (bool) -- whether the game exists in the local library.
    - `localGameId` (string, nullable) -- the local game ID if `inLibrary` is true.
    - `coverUrl` (string, nullable) -- local cover art URL if in library.
- Games are sorted by release date (if available) or alphabetically.
- Returns 404 if the series is not found.

#### Tests
- Unit tests verify: series list only includes series with local games, counts are correct, detail endpoint returns both local and non-local entries.

---

## Story 8: API endpoint -- list franchises in the library

**As a** future Franchise page UI (Phase 4),
**I want** an endpoint that lists all franchises present in the library,
**so that** I can display franchise browsing.

### Acceptance Criteria

#### Endpoint: `GET /api/franchises`
- Requires authentication.
- Returns a JSON array of franchises that have at least one game in the local library.
- Each entry includes:
  - `id` (string) -- the IGDB franchise ID.
  - `name` (string) -- e.g., "Mario", "Zelda".
  - `gameCount` (int) -- number of games in the local library in this franchise.
- Sorted by `gameCount` descending.

#### Endpoint: `GET /api/franchises/:id`
- Returns the franchise detail with all local games.
- Response includes:
  - `id`, `name`.
  - `games` -- array of local games in this franchise, using the `GameResponse` format.
- Games sorted by release date (if available) or alphabetically.
- Returns 404 if no franchise with the given ID exists in the library.

#### Tests
- Unit tests verify: correct game counts, sorting, detail includes all matching games, 404 for unknown franchise.

---

## Story 9: Extend `GET /api/games` with new filter parameters

**As a** developer building filtering UIs in later phases,
**I want** the existing games list endpoint to support filtering by theme, keyword, and player perspective,
**so that** the same endpoint can power both the current library view and future filtered views.

### Acceptance Criteria

#### New query parameters on `GET /api/games`
- `theme` (string) -- IGDB theme ID. When provided, only games tagged with this theme are returned.
- `keyword` (string) -- IGDB keyword ID. When provided, only games tagged with this keyword are returned.
- `perspective` (string) -- IGDB perspective ID. When provided, only games tagged with this perspective are returned.
- Multiple filters can be combined (AND logic): `?theme=17&perspective=4` returns games that are Fantasy AND First person.
- Existing filters (console, genre, search) continue to work and can be combined with the new filters.
- When no new filters are provided, the endpoint behaves identically to today (no regression).

#### Performance
- Filters use JOIN queries against the new tables rather than loading all games into memory and filtering in Go.
- The queries are bounded by the existing pagination (page/pageSize).

#### Tests
- Unit tests verify:
  - Filtering by a single theme returns correct games.
  - Filtering by a single keyword returns correct games.
  - Combining theme + perspective filters returns the intersection.
  - Combining new filters with existing console filter works.
  - No regression: calling without new filters returns the same results as before.

---

## Story 10: IGDB unavailability does not break existing features

**As a** server admin,
**I want** the enrichment to be resilient to IGDB outages,
**so that** existing scraping, game browsing, and the Explore page continue to work when IGDB is down.

### Acceptance Criteria

#### During normal scraping
- If `GetGameFull` fails during `ScrapeGame`, the game is still scraped successfully with basic metadata (title, description, cover, screenshots from the initial IGDB search + LibRetro).
- The scrape is counted as a success, not a failure, in the progress counters.
- A warning is logged with the game name and error.

#### During backfill
- If `GetGameFull` fails for a specific game during the backfill, that game is skipped and counted as a failure in the progress.
- The backfill continues to the next game.
- If IGDB returns repeated auth failures (e.g., expired token), the client re-authenticates automatically (existing behavior in `authenticate()`).

#### API endpoints
- The new read endpoints (`/api/themes`, `/api/keywords`, `/api/series`, `/api/franchises`) always read from the local database. They never make live IGDB calls.
- If no enrichment data exists (e.g., IGDB was never configured), these endpoints return empty arrays, not errors.
- The game filter parameters (`theme`, `keyword`, `perspective`) on `GET /api/games` return empty results when no enrichment data exists, not errors.

#### Tests
- Unit tests with a mock IGDB server that returns errors verify:
  - Scrape completes successfully despite enrichment failure.
  - Backfill skips failed games and completes.
  - Read endpoints return empty arrays when no enrichment data exists.

---

## Non-functional requirements

### Rate limiting
- The enrichment backfill must not exceed IGDB's 4 requests/second limit. The existing rate ticker in the IGDB client enforces this; no additional throttling is needed, but the backfill must use the same client instance (not create a new one that bypasses the limiter).
- For games that require multiple IGDB calls (game data + collection lookup + franchise lookup), each call independently waits on the rate limiter. A game that needs 3 calls uses 3 rate slots (750ms minimum).

### Database performance
- New tables have appropriate indexes on foreign keys and columns used in WHERE/JOIN clauses (as specified in the unique constraints above).
- The `GET /api/themes` and `GET /api/keywords` endpoints use aggregate SQL queries (GROUP BY + COUNT) rather than loading all rows into memory.

### Backward compatibility
- Existing API responses are unchanged. No existing field is removed or renamed.
- The `GameResponse` struct is not modified in this phase. Enrichment data is only available via the new dedicated endpoints.
- Existing scrape functionality (ScrapeGame, ScrapeAll, ScrapeGameWithIGDBMatch) continues to work identically for users who have not configured IGDB credentials -- the enrichment steps are simply skipped.

### Observability
- All new IGDB API calls are logged at INFO level with the game name and IGDB ID (following the existing logging pattern in `client.go`).
- Enrichment failures are logged at WARN level.
- The backfill logs a summary at completion: total games, successes, failures, duration.
