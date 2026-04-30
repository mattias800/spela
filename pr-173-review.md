# PR #173 Review: feat/local-image-caching

**Reviewer:** code-reviewer
**Branch:** feat/local-image-caching
**Commits reviewed:** 3 (8058997f, 56da8632, 7c7c1eb4)

---

## Summary

This PR eliminates direct external image CDN URLs (IGDB + SteamGridDB) from public API responses by caching images locally. It also includes two unrelated changes (console generation grouping, platform-specific release dates) that should ideally be separate commits/PRs.

---

## Critical Issues

### 1. Synchronous image downloads block API request handlers
**Files:** `server/internal/api/console_handler.go:670-678`, `server/internal/api/game_discovery_handler.go:112-117`

`upsertTopRatedGames()` and `upsertSimilarGames()` are called synchronously during GET request handling. Each call downloads N cover images via `DownloadExternalImage()`, making HTTP requests to IGDB CDN. For 25 top-rated games, this could add 10-30 seconds to a user-facing GET request.

**Recommendation:** Run the image download loop in a background goroutine (fire-and-forget), or download lazily on first access. The `triggerCompanyFetch` in `explore_handler_company.go:69` already uses this pattern correctly with `go func()`.

### 2. External URL fallback leaks CDN URLs in two places
**File:** `server/internal/scraper/scraper_igdb.go:353-355` -- Company logo falls back to CDN URL when download fails:
```go
logoPath = cdnURL // fallback to CDN URL if download fails
```
This stores an external URL in the DB, defeating the purpose of the PR. When `resolveImageURL()` sees an `http` prefix, it passes it through unchanged, so external URLs still leak to clients.

**File:** `server/internal/api/explore_handler_company.go:101-102` -- Same pattern: falls back to `igdb.CompanyLogoURL()` when scraper is nil or download fails.

**File:** `server/internal/api/explore_handler_gallery.go:219` -- Falls back to `igdb.ImageURL()` when `LocalPath` is empty.

**Recommendation:** If the goal is to eliminate all external URLs, these fallbacks should return empty strings rather than CDN URLs. If graceful degradation is intended, document it explicitly and ensure `resolveImageURL` is always applied.

---

## Major Issues

### 3. N+1 query problem in backfill endpoint
**File:** `server/internal/api/admin_handler_backfill.go:43-75, 107-117`

For each GameArtwork and GameArtworkImage, a separate `h.DB.Preload("Console").First(&game, ...)` query is executed. For a library with thousands of games, this creates thousands of individual DB queries.

**Recommendation:** Pre-load all games with consoles in a single query and build a lookup map: `gameMap := map[uint]db.Game{}`.

### 4. Backfill endpoint has no timeout / cancellation / progress
**File:** `server/internal/api/admin_handler_backfill.go`

This endpoint downloads potentially hundreds of images synchronously during one HTTP request. It could easily time out. Contrast with `EnrichAll()` in `scraper_batch.go` which has progress callbacks and `ScrapeAll()` which accepts a `context.Context` for cancellation.

**Recommendation:** Either (a) run in a background goroutine with WebSocket progress (like enrichment), or (b) add a `context.Context` with timeout and process in batches with intermediate JSON responses.

### 5. Code duplication between backfill and scraper
**File:** `server/internal/api/admin_handler_backfill.go:48-74` vs `server/internal/scraper/scraper_batch.go:159-179`

The artwork download logic (hero, grid, logo, icon subpath construction) is duplicated verbatim between the backfill handler and `scrapeSteamGridDBArtwork`. The subpath format strings are hardcoded in both places.

**Recommendation:** Extract a shared helper like `downloadArtworkImages(scraper, gameID, consoleAbbr, artwork) map[string]string` in the scraper package that both call sites use.

### 6. No test for the backfill endpoint
**File:** No `admin_handler_backfill_test.go` exists.

The backfill endpoint is a new admin endpoint with significant logic (5 different entity types, DB queries, external downloads, updates). It has zero test coverage.

**Recommendation:** Add at least one integration test that seeds external URLs, calls the endpoint with a mock HTTP server, and verifies the URLs are replaced with local paths.

---

## Minor Issues

### 7. Inconsistent `consoleAbbr` casing
**File:** `server/internal/api/admin_handler_backfill.go:47` uses `strings.ToLower(game.Console.Abbreviation)`, but `server/internal/scraper/scraper_batch.go:161` uses `console.Abbreviation` without lowercasing. This could create duplicate files under different directory names (e.g., `NES/` vs `nes/`).

**Recommendation:** Standardize on one convention (lowercase) across all subpath construction.

### 8. Backfill artwork gallery index uses loop variable `i`
**File:** `server/internal/api/admin_handler_backfill.go:113`
```go
subpath := fmt.Sprintf("%s/%d/artwork_%d.jpg", consoleAbbr, game.ID, i)
```
The `i` here is the position in the query result set, not a stable identifier. If the set of images changes between runs, existing files could be overwritten with different images. The scraper enrichment at `scraper_enrichment.go:109` has the same issue.

**Recommendation:** Use the IGDB image ID (or a hash of it) in the filename for stability.

### 9. PR contains unrelated changes
The PR bundles three separate features:
- Local image caching (the main feature)
- Console generation grouping (56da8632)
- Platform-specific release dates + console icons (7c7c1eb4)

Per CLAUDE.md: "One logical change per commit." These should be separate PRs for cleaner review and easier revert.

### 10. `DownloadExternalImage` was previously private (`downloadExternalImage`)
**File:** `server/internal/scraper/scraper_igdb.go:426,446`

The method was renamed from `downloadExternalImage` to `DownloadExternalImage` (exported). This is correct since API handlers now need to call it. However, the public API surface of the `Scraper` type grows. Consider whether an interface would be more appropriate for the API handler dependency.

### 11. Missing `json:"-"` inconsistency
**File:** `server/internal/db/models.go:577,597,805`

`CoverLocalPath` and `LocalPath` are correctly tagged `json:"-"` so they never appear in JSON responses. Good.

### 12. SimilarGame model formatting inconsistency
**File:** `server/internal/db/models.go:594-599`

The diff introduces inconsistent alignment:
```go
CoverImageID   string         `gorm:"size:128" json:"coverImageId"`
CoverLocalPath string         `gorm:"size:512" json:"-"`
Rating         float64        `json:"rating"`
```
while the surrounding fields use different alignment. Minor formatting issue.

---

## Security Assessment

- **Path traversal:** `Storage.WriteImage()` has proper path traversal protection (verifies resolved path is inside image directory). Good.
- **SSRF via `DownloadExternalImage`:** The `imageURL` parameter comes from DB-stored values (IGDB/SteamGridDB URLs), not user input. The backfill endpoint is admin-only. Acceptable risk.
- **Auth on backfill:** Correctly placed under the `admin` route group which requires admin auth middleware. Good.
- **`resolveImageURL` passthrough:** When path starts with `http`, it returns it unchanged. This is intentional for backward compatibility but contradicts the PR's stated goal.

---

## Test Coverage Assessment

- **New test:** `server/internal/scraper/scraper_igdb_test.go` (148 lines) -- tests IGDB scraping with mock server. Good addition.
- **Updated test:** `game_discovery_handler_test.go` -- updated expectations for local paths. Good.
- **Missing:** No test for `admin_handler_backfill.go` (142 lines of new code).
- **Missing:** No test for `resolveImageURL()` helper function.
- **Missing:** No test for the fallback behavior in `explore_handler_gallery.go`.

---

## Verdict

**Request changes.** The synchronous image downloads blocking API requests (issue #1) and external URL fallback leaks (issue #2) are critical and must be addressed before merge. The missing backfill test (issue #6) should also be added.
