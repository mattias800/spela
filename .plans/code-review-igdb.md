# Code Review: IGDB Integration

## Summary

Reviewed all files listed in the task. The integration is well-structured overall: clean separation of concerns, good test coverage, proper secret masking, and thorough ScreenScraper removal. Below are the issues found, ordered by severity.

---

## BUGS (must fix)

### BUG-1: Masked secret gets saved back to DB, destroying the real secret (CRITICAL)

**Files:** `server/internal/api/admin_handler.go:193-206`, `web/src/pages/admin/settings-page.tsx:58-73`

The `GET /api/admin/settings` endpoint returns `"********"` for `igdb_client_secret`. The frontend loads this into state (`setIgdbClientSecret(settings["igdb_client_secret"] ?? "")`). When the user clicks "Save Settings" without changing the secret, the frontend sends `"********"` back to `PUT /api/admin/settings`, which writes it to the DB as the literal string `"********"`, destroying the real secret.

**Fix:** In `UpdateSettings`, skip writing secret keys when the submitted value equals the mask placeholder:

```go
for key, value := range req {
    if secretSettingKeys[key] && value == "********" {
        continue // don't overwrite with mask placeholder
    }
    setting := db.ServerSetting{Key: key, Value: value}
    h.DB.Where("key = ?", key).Assign(setting).FirstOrCreate(&setting)
}
```

### BUG-2: `time.Tick` leaks a ticker that can never be garbage collected

**File:** `server/internal/igdb/client.go:86`

`time.Tick` creates a `*time.Ticker` that is never stopped. The Go docs explicitly warn: "the underlying Ticker cannot be recovered by the garbage collector; it leaks." If clients are created and discarded (e.g., in `TestIGDB` handler or `tryConfigureIGDB` which creates a new client every time credentials change), each leaked ticker accumulates goroutines.

**Fix:** Use `time.NewTicker` and store it on the struct, or use a `*rate.Limiter` from `golang.org/x/time/rate` which doesn't leak. Since the `Client` has no `Close()` method today, the simplest approach is to use `rate.NewLimiter(4, 1)` instead:

```go
import "golang.org/x/time/rate"

type Client struct {
    // ...
    limiter *rate.Limiter
}

func NewClient(clientID, clientSecret string) *Client {
    return &Client{
        // ...
        limiter: rate.NewLimiter(4, 1), // 4 req/s, burst of 1
    }
}

// In SearchGame:
if err := c.limiter.Wait(context.Background()); err != nil {
    return nil, fmt.Errorf("rate limiter: %w", err)
}
```

This also affects test code (`client_test.go`) where `time.Tick(time.Millisecond)` is used extensively, though test leaks are less critical.

---

## SECURITY ISSUES

### SEC-1: IGDB query injection via game filename

**File:** `server/internal/igdb/client.go:231-233`, `server/internal/igdb/client.go:271-273`

The `escapeQuery` function only escapes double quotes, but IGDB's Apicalypse query language also treats semicolons as statement terminators. A ROM filename containing a semicolon (e.g., `Game; delete *;.nes`) would produce:

```
search "Game; delete *;"; fields name,...
```

This could potentially allow query injection into the IGDB API. While IGDB's API likely limits what statements are accepted, it's still a defense-in-depth concern.

**Fix:** Also strip/escape semicolons in `escapeQuery`, or better yet, remove all non-alphanumeric/space characters from the search term:

```go
func escapeQuery(s string) string {
    s = strings.ReplaceAll(s, `"`, `\"`)
    s = strings.ReplaceAll(s, ";", "")
    return s
}
```

### SEC-2: `GetIGDBStatus` makes a live Twitch API call on every request

**File:** `server/internal/api/admin_igdb_handler.go:42-79`

Every `GET /api/admin/igdb/status` call fetches credentials from the DB and makes a live `TestCredentials` call to Twitch. Since this endpoint is called from the dashboard, sidebar, and settings page, an admin could inadvertently trigger many Twitch API calls. While it's behind admin auth, consider caching the status or only testing on explicit user action (the "Test Connection" button already covers this).

**Recommendation:** Change `GetIGDBStatus` to only check whether credentials are present in the DB (the `configured` flag), without actually calling Twitch. The live validation is already covered by the `/igdb/test` endpoint.

---

## CODE QUALITY

### CQ-1: `tryConfigureIGDB` creates a new IGDB client on every scrape if credentials changed

**File:** `server/internal/api/admin_handler.go:337-353`

The check `if h.Scraper.IGDBClient != nil && h.Scraper.IGDBClient.IsConfigured()` only short-circuits if the client already exists and is configured. But if the admin updates credentials, the cached client still has the old credentials and `IsConfigured()` returns true, so the new credentials are never picked up until the server restarts.

**Fix:** Either compare the stored client's credentials against the DB values, or always refresh from DB when triggering a scrape.

### CQ-2: `testing.go` exports setters for package-level vars, which is not thread-safe

**File:** `server/internal/igdb/testing.go`

The `Set*ForTest` functions mutate package-level variables without synchronization. If tests run in parallel (`t.Parallel()`), they'll race on these globals. The tests currently don't use `t.Parallel()`, but this is a latent issue.

**Recommendation:** Either document that these tests must not be parallelized, or use `t.Setenv`-style cleanup patterns. Since Go test files in the same package run serially by default, this is low priority.

### CQ-3: `AGENT_TEAM.md` still references ScreenScraper

**File:** `AGENT_TEAM.md:46`

Line 46 says: "Metadata scraping (LibRetro Thumbnails, ScreenScraper)". Should be updated to "(LibRetro Thumbnails, IGDB)".

### CQ-4: `igdbImageFound` variable is assigned but never meaningfully used

**File:** `server/internal/scraper/scraper.go:163,170,199`

```go
igdbImageFound := false
// ...
igdbImageFound = game.CoverURL != "" || game.ScreenshotURL != ""
// ...
_ = igdbImageFound // used for logging clarity
```

This variable is assigned, then blanked with `_ =`. It serves no purpose and should be removed.

### CQ-5: `Players` field mapping is semantically incorrect

**File:** `server/internal/scraper/scraper.go:269-271`

```go
if len(match.GameModes) > 0 && game.Players == 0 {
    game.Players = len(match.GameModes)
}
```

IGDB `game_modes` contains entries like "Single player", "Multiplayer", "Co-operative", etc. A game with modes ["Single player", "Multiplayer", "Co-operative"] would get `Players = 3`, which doesn't represent the number of players. The `Players` field typically means max simultaneous players.

**Fix:** Either remove this mapping (leave `Players` unset from IGDB) or look for a `multiplayer_modes` field in IGDB that has actual player counts.

### CQ-6: Raw `<button>` in settings page and IGDB config card

**Files:** `web/src/pages/admin/settings-page.tsx:110-113`, `web/src/features/admin/components/igdb-config-card.tsx:104-113`

Per CLAUDE.md and AGENT_TEAM.md UI rules, raw `<button>` elements in page/feature files should use the shared `Button` component. The settings page has a raw delete button for game directories, and the IGDB config card has a raw `<button>` for the instructions toggle.

**Recommendation:** Replace with appropriate `Button` variants (e.g., `variant="ghost"` with `size="sm"`).

### CQ-7: No test for `UpdateSettings` with masked secret round-trip

**File:** `server/internal/api/admin_igdb_test.go`

There's a test for masking in GET responses but no test verifying that PUT with `"********"` doesn't overwrite the real secret. After fixing BUG-1, add a round-trip test.

---

## POSITIVE OBSERVATIONS

1. **Secret masking is implemented correctly** in `GetSettings` with a clean `secretSettingKeys` map approach (aside from the round-trip bug).
2. **Thread safety** for OAuth token management is properly handled with `sync.Mutex` in `authenticate()` and `fetchToken()`.
3. **Rate limiting** at 4 req/s is correctly implemented for IGDB's documented limit.
4. **Clean ScreenScraper removal** -- no remnants in server or web source code (only the test that asserts absence).
5. **Test coverage** is thorough: table-driven tests, error cases, network failures, rate limiting verification, empty results.
6. **Clean architecture boundaries**: `igdb` package has no web framework imports, handler uses it through a clean interface.
7. **Frontend component composition** follows project conventions well: feature-specific components in `features/admin/components/`, hooks in `hooks/`, proper TypeScript typing.
8. **`CleanGameName`** is well-implemented with iterative bracket/parenthesis removal and good test cases.

---

## VERDICT

**Request changes** -- BUG-1 (masked secret overwrite) is a data-destroying bug that must be fixed before merge. BUG-2 (time.Tick leak) and SEC-1 (query injection) should also be addressed. The remaining items are improvements that can be addressed in follow-up.
