# Scrape Status Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track scrape outcomes per source per game in a `game_scrape_results` table, add a status counts API, update the scraper to record results, migrate existing data, and add a status dashboard card to the admin web UI.

**Architecture:** New `GameScrapeResult` model with `(game_id, source)` unique index. Scraper upserts results after each source runs. Admin API returns counts grouped by source and status. Web UI shows a breakdown card with action buttons. Migration backfills from existing `scraper_id` field.

**Tech Stack:** Go (GORM, Gin), React (TanStack Query, Tailwind), SQLite

---

### Task 1: Add GameScrapeResult model and migration

**Files:**
- Modify: `server/internal/db/models.go`
- Modify: `server/internal/db/database.go`
- Test: `server/internal/db/database_test.go` (if exists)

- [ ] **Step 1: Add GameScrapeResult struct to models.go**

Add after the `GameAgeRating` struct (around line 830):

```go
// GameScrapeResult tracks the outcome of a scrape attempt for a specific source.
// One row per game per source (igdb, libretro, steamgriddb).
type GameScrapeResult struct {
	ID            uint       `gorm:"primarykey" json:"id"`
	CreatedAt     time.Time  `json:"createdAt"`
	UpdatedAt     time.Time  `json:"updatedAt"`
	GameID        uint       `gorm:"uniqueIndex:idx_scrape_result_game_source;not null" json:"gameId"`
	Source        string     `gorm:"uniqueIndex:idx_scrape_result_game_source;size:32;not null" json:"source"`
	Status        string     `gorm:"size:32;not null" json:"status"`
	SourceID      string     `gorm:"size:128" json:"sourceId,omitempty"`
	LastAttemptAt *time.Time `json:"lastAttemptAt,omitempty"`
	ErrorMessage  string     `gorm:"size:512" json:"errorMessage,omitempty"`
}
```

- [ ] **Step 2: Add GameScrapeResult to AutoMigrate**

In `database.go`, add `&GameScrapeResult{}` to the `AutoMigrate` call (after the last model, around line 161).

- [ ] **Step 3: Add migration function to backfill existing data**

Add to `database.go`:

```go
// MigrateScrapeResults backfills game_scrape_results from existing scraper_id values.
// Safe to call multiple times — skips games that already have results.
func MigrateScrapeResults(database *gorm.DB) error {
	// Count existing results; skip if already populated
	var count int64
	database.Model(&GameScrapeResult{}).Count(&count)
	if count > 0 {
		slog.Info("scrape results already populated", "count", count)
		return nil
	}

	// Games with IGDB match
	var igdbGames []Game
	database.Where("scraper_id LIKE 'igdb:%' AND scrape_attempts > 0").Find(&igdbGames)
	for _, g := range igdbGames {
		now := g.UpdatedAt
		igdbID := strings.TrimPrefix(g.ScraperID, "igdb:")
		igdbID = strings.TrimSuffix(igdbID, ":propagated")
		database.Clauses(clause.OnConflict{DoNothing: true}).Create(&GameScrapeResult{
			GameID: g.ID, Source: "igdb", Status: "matched",
			SourceID: igdbID, LastAttemptAt: &now,
		})
		// LibRetro: matched if cover exists
		if g.LibRetroCoverURL != "" || g.CoverURL != "" {
			database.Clauses(clause.OnConflict{DoNothing: true}).Create(&GameScrapeResult{
				GameID: g.ID, Source: "libretro", Status: "matched", LastAttemptAt: &now,
			})
		}
		// SteamGridDB: matched if hero exists
		if g.HeroURL != "" {
			database.Clauses(clause.OnConflict{DoNothing: true}).Create(&GameScrapeResult{
				GameID: g.ID, Source: "steamgriddb", Status: "matched", LastAttemptAt: &now,
			})
		}
	}

	// Games with LibRetro fallback (no IGDB match)
	var fallbackGames []Game
	database.Where("scraper_id = 'libretro' AND scrape_attempts > 0").Find(&fallbackGames)
	for _, g := range fallbackGames {
		now := g.UpdatedAt
		database.Clauses(clause.OnConflict{DoNothing: true}).Create(&GameScrapeResult{
			GameID: g.ID, Source: "igdb", Status: "not_found", LastAttemptAt: &now,
		})
		database.Clauses(clause.OnConflict{DoNothing: true}).Create(&GameScrapeResult{
			GameID: g.ID, Source: "libretro", Status: "matched", LastAttemptAt: &now,
		})
		if g.HeroURL != "" {
			database.Clauses(clause.OnConflict{DoNothing: true}).Create(&GameScrapeResult{
				GameID: g.ID, Source: "steamgriddb", Status: "matched", LastAttemptAt: &now,
			})
		}
	}

	// Games with empty scraper_id but attempts > 0 (tried, nothing found)
	var emptyGames []Game
	database.Where("(scraper_id = '' OR scraper_id IS NULL) AND scrape_attempts > 0").Find(&emptyGames)
	for _, g := range emptyGames {
		now := g.UpdatedAt
		database.Clauses(clause.OnConflict{DoNothing: true}).Create(&GameScrapeResult{
			GameID: g.ID, Source: "igdb", Status: "not_found", LastAttemptAt: &now,
		})
		database.Clauses(clause.OnConflict{DoNothing: true}).Create(&GameScrapeResult{
			GameID: g.ID, Source: "libretro", Status: "not_found", LastAttemptAt: &now,
		})
	}

	var resultCount int64
	database.Model(&GameScrapeResult{}).Count(&resultCount)
	slog.Info("migrated scrape results", "results", resultCount)
	return nil
}
```

Add `"strings"` and `"gorm.io/gorm/clause"` imports if not already present.

- [ ] **Step 4: Call migration on startup**

In `server/cmd/server/main.go` (or wherever startup runs), call `db.MigrateScrapeResults(database)` after `AutoMigrate` and `SeedConsoles`/`SeedCores`.

- [ ] **Step 5: Run tests**

Run: `cd server && go test ./internal/db/... -v`
Expected: All pass.

- [ ] **Step 6: Commit**

```bash
git add server/internal/db/models.go server/internal/db/database.go server/cmd/server/main.go
git commit -m "feat: add GameScrapeResult model and migration from existing scraper_id"
```

---

### Task 2: Update scraper to record per-source results

**Files:**
- Modify: `server/internal/scraper/scraper_igdb.go`
- Create: `server/internal/scraper/scrape_result.go`
- Test: `server/internal/scraper/scrape_result_test.go`

- [ ] **Step 1: Create helper for upserting scrape results**

Create `server/internal/scraper/scrape_result.go`:

```go
package scraper

import (
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// RecordScrapeResult upserts a scrape result for the given game and source.
func RecordScrapeResult(database *gorm.DB, gameID uint, source, status, sourceID, errorMsg string) {
	now := time.Now()
	result := db.GameScrapeResult{
		GameID:        gameID,
		Source:        source,
		Status:        status,
		SourceID:      sourceID,
		LastAttemptAt: &now,
		ErrorMessage:  errorMsg,
	}
	database.Clauses(clause.OnConflict{
		Columns:   []clause.Column{{Name: "game_id"}, {Name: "source"}},
		DoUpdates: clause.AssignmentColumns([]string{"status", "source_id", "last_attempt_at", "error_message", "updated_at"}),
	}).Create(&result)
}
```

- [ ] **Step 2: Write test for RecordScrapeResult**

Create `server/internal/scraper/scrape_result_test.go`:

```go
package scraper

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func setupResultTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	err = database.AutoMigrate(&db.Game{}, &db.Console{}, &db.GameScrapeResult{})
	require.NoError(t, err)
	return database
}

func TestRecordScrapeResult_CreatesNewRow(t *testing.T) {
	database := setupResultTestDB(t)
	database.Create(&db.Game{Title: "Test Game"})

	RecordScrapeResult(database, 1, "igdb", "matched", "1234", "")

	var result db.GameScrapeResult
	err := database.Where("game_id = ? AND source = ?", 1, "igdb").First(&result).Error
	require.NoError(t, err)
	assert.Equal(t, "matched", result.Status)
	assert.Equal(t, "1234", result.SourceID)
	assert.NotNil(t, result.LastAttemptAt)
}

func TestRecordScrapeResult_UpsertsExisting(t *testing.T) {
	database := setupResultTestDB(t)
	database.Create(&db.Game{Title: "Test Game"})

	RecordScrapeResult(database, 1, "igdb", "not_found", "", "")
	RecordScrapeResult(database, 1, "igdb", "matched", "5678", "")

	var results []db.GameScrapeResult
	database.Where("game_id = ? AND source = ?", 1, "igdb").Find(&results)
	assert.Len(t, results, 1)
	assert.Equal(t, "matched", results[0].Status)
	assert.Equal(t, "5678", results[0].SourceID)
}

func TestRecordScrapeResult_MultipleSources(t *testing.T) {
	database := setupResultTestDB(t)
	database.Create(&db.Game{Title: "Test Game"})

	RecordScrapeResult(database, 1, "igdb", "matched", "1234", "")
	RecordScrapeResult(database, 1, "libretro", "matched", "", "")
	RecordScrapeResult(database, 1, "steamgriddb", "not_found", "", "")

	var results []db.GameScrapeResult
	database.Where("game_id = ?", 1).Find(&results)
	assert.Len(t, results, 3)
}

func TestRecordScrapeResult_RecordsError(t *testing.T) {
	database := setupResultTestDB(t)
	database.Create(&db.Game{Title: "Test Game"})

	RecordScrapeResult(database, 1, "igdb", "error", "", "connection timeout")

	var result db.GameScrapeResult
	database.Where("game_id = ? AND source = ?", 1, "igdb").First(&result)
	assert.Equal(t, "error", result.Status)
	assert.Equal(t, "connection timeout", result.ErrorMessage)
}
```

- [ ] **Step 3: Run tests**

Run: `cd server && go test ./internal/scraper/... -run TestRecordScrapeResult -v`
Expected: All 4 tests pass.

- [ ] **Step 4: Integrate into ScrapeGame**

In `server/internal/scraper/scraper_igdb.go`, update `ScrapeGame()` to call `RecordScrapeResult` at each outcome point:

After IGDB match (where `applyIGDBMatch` is called):
```go
RecordScrapeResult(s.DB, game.ID, "igdb", "matched", fmt.Sprintf("%d", match.ID), "")
```

After IGDB returns nil (no match, before LibRetro fallback):
```go
RecordScrapeResult(s.DB, game.ID, "igdb", "not_found", "", "")
```

After IGDB returns an error:
```go
RecordScrapeResult(s.DB, game.ID, "igdb", "error", "", err.Error())
```

After LibRetro art download (successful):
```go
RecordScrapeResult(s.DB, game.ID, "libretro", "matched", "", "")
```

After LibRetro art not found:
```go
RecordScrapeResult(s.DB, game.ID, "libretro", "not_found", "", "")
```

After SteamGridDB art (successful):
```go
RecordScrapeResult(s.DB, game.ID, "steamgriddb", "matched", "", "")
```

After SteamGridDB art not found or error:
```go
RecordScrapeResult(s.DB, game.ID, "steamgriddb", "not_found", "", "")
// or for errors:
RecordScrapeResult(s.DB, game.ID, "steamgriddb", "error", "", err.Error())
```

- [ ] **Step 5: Run full scraper tests**

Run: `cd server && go test ./internal/scraper/... -v`
Expected: All pass.

- [ ] **Step 6: Commit**

```bash
git add server/internal/scraper/scrape_result.go server/internal/scraper/scrape_result_test.go server/internal/scraper/scraper_igdb.go
git commit -m "feat: record per-source scrape results in ScrapeGame"
```

---

### Task 3: Add scrape status counts API endpoint

**Files:**
- Modify: `server/internal/api/admin_handler_scraper.go`
- Modify: `server/internal/api/router.go`

- [ ] **Step 1: Add ScrapeStatusCounts handler**

Add to `admin_handler_scraper.go`:

```go
// ScrapeStatusCounts returns a breakdown of scrape results by source and status.
func (h *AdminHandler) ScrapeStatusCounts(c *gin.Context) {
	type SourceStatus struct {
		Source string `json:"source"`
		Status string `json:"status"`
		Count  int64  `json:"count"`
	}

	var counts []SourceStatus
	h.DB.Model(&db.GameScrapeResult{}).
		Select("source, status, COUNT(*) as count").
		Group("source, status").
		Scan(&counts)

	// Get total game count for "not_attempted" calculation
	var totalGames int64
	h.DB.Model(&db.Game{}).Count(&totalGames)

	// Calculate eligible counts (outside 7-day cooldown)
	cooldownCutoff := time.Now().AddDate(0, 0, -7)
	type EligibleCount struct {
		Source string `json:"source"`
		Status string `json:"status"`
		Count  int64  `json:"count"`
	}
	var eligible []EligibleCount
	h.DB.Model(&db.GameScrapeResult{}).
		Select("source, status, COUNT(*) as count").
		Where("status IN ('not_found', 'error')").
		Where("last_attempt_at IS NULL OR last_attempt_at < ?", cooldownCutoff).
		Group("source, status").
		Scan(&eligible)

	eligibleMap := make(map[string]int64)
	for _, e := range eligible {
		eligibleMap[e.Source+":"+e.Status] = e.Count
	}

	// Build per-source summary
	sources := []string{"igdb", "libretro", "steamgriddb"}
	type SourceSummary struct {
		Source           string `json:"source"`
		Matched          int64  `json:"matched"`
		NotFound         int64  `json:"notFound"`
		NotFoundEligible int64  `json:"notFoundEligible"`
		Error            int64  `json:"error"`
		ErrorEligible    int64  `json:"errorEligible"`
		NotAttempted     int64  `json:"notAttempted"`
	}

	var result []SourceSummary
	for _, src := range sources {
		summary := SourceSummary{Source: src}
		var tracked int64
		for _, sc := range counts {
			if sc.Source != src {
				continue
			}
			switch sc.Status {
			case "matched":
				summary.Matched = sc.Count
			case "not_found":
				summary.NotFound = sc.Count
			case "error":
				summary.Error = sc.Count
			}
			tracked += sc.Count
		}
		summary.NotAttempted = totalGames - tracked
		if summary.NotAttempted < 0 {
			summary.NotAttempted = 0
		}
		summary.NotFoundEligible = eligibleMap[src+":not_found"]
		summary.ErrorEligible = eligibleMap[src+":error"]
		result = append(result, summary)
	}

	c.JSON(http.StatusOK, gin.H{"sources": result})
}
```

Add `"time"` to imports if not present.

- [ ] **Step 2: Register route**

In `router.go`, add after the existing scrape routes:

```go
admin.GET("/scrape/counts", adminHandler.ScrapeStatusCounts)
```

- [ ] **Step 3: Extend TriggerScrape to accept source+status params**

In the existing `TriggerScrape` handler, add source+status filtering before the existing mode switch:

```go
// Source+status based filtering (new)
source := c.Query("source")
status := c.Query("status")
if source != "" && status != "" {
	cooldownCutoff := time.Now().AddDate(0, 0, -7)
	subQuery := h.DB.Model(&db.GameScrapeResult{}).
		Select("game_id").
		Where("source = ? AND status = ?", source, status)
	if status == "not_found" || status == "error" {
		subQuery = subQuery.Where("last_attempt_at IS NULL OR last_attempt_at < ?", cooldownCutoff)
	}
	q = q.Where("id IN (?)", subQuery)
	q.Count(&total)
	// ... proceed with scrape using this filtered set
}
```

- [ ] **Step 4: Run tests**

Run: `cd server && go test ./internal/api/... -v`
Expected: All pass.

- [ ] **Step 5: Commit**

```bash
git add server/internal/api/admin_handler_scraper.go server/internal/api/router.go
git commit -m "feat: add scrape status counts API and source+status filtering"
```

---

### Task 4: Web UI — scrape status dashboard card

**Files:**
- Create: `web/src/features/admin/components/scrape-status-card.tsx`
- Modify: `web/src/pages/admin/scan-page.tsx`
- Modify: `web/src/hooks/use-admin.ts`
- Modify: `web/src/lib/api-routes.ts`

- [ ] **Step 1: Add API route and hook**

In `api-routes.ts`, add to `ApiGetPath`:
```typescript
| "/admin/scrape/counts"
```

In `use-admin.ts`, add:

```typescript
export interface ScrapeSourceCounts {
  source: string;
  matched: number;
  notFound: number;
  notFoundEligible: number;
  error: number;
  errorEligible: number;
  notAttempted: number;
}

export interface ScrapeStatusCountsResponse {
  sources: ScrapeSourceCounts[];
}

export function useScrapeStatusCounts() {
  return useQuery({
    queryKey: ["admin", "scrape-counts"],
    queryFn: () => api.get<ScrapeStatusCountsResponse>("/admin/scrape/counts"),
  });
}
```

Update `ScrapeMode` type:
```typescript
export type ScrapeMode = "new" | "all" | "fallback";

// Extend useScrapeMetadata to accept source+status params
export function useScrapeMetadata() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      mode = "new",
      console,
      source,
      status,
    }: {
      mode?: ScrapeMode;
      console?: string;
      source?: string;
      status?: string;
    }) => {
      const params = new URLSearchParams();
      if (mode !== "new") params.set("mode", mode);
      if (console) params.set("console", console);
      if (source) params.set("source", source);
      if (status) params.set("status", status);
      const qs = params.toString();
      return api.post<ScrapeStartResponse>(
        qs ? `/admin/scrape?${qs}` : "/admin/scrape",
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "scrape-counts"] });
    },
  });
}
```

- [ ] **Step 2: Create ScrapeStatusCard component**

Create `web/src/features/admin/components/scrape-status-card.tsx`:

```tsx
import { Card, CardHeader, CardContent, Button } from "@/components/ui";
import { useScrapeStatusCounts, useScrapeMetadata, useScrapeStatus } from "@/hooks/use-admin";
import { Skeleton } from "@/components/ui";
import { RefreshCw } from "lucide-react";

const SOURCE_LABELS: Record<string, string> = {
  igdb: "IGDB",
  libretro: "LibRetro Thumbnails",
  steamgriddb: "SteamGridDB",
};

export function ScrapeStatusCard() {
  const { data, isLoading } = useScrapeStatusCounts();
  const scrape = useScrapeMetadata();
  const { data: scrapeStatus } = useScrapeStatus();
  const isActive = scrapeStatus?.active ?? false;

  if (isLoading) {
    return <Skeleton className="h-64 w-full rounded-2xl" />;
  }

  return (
    <Card>
      <CardHeader>
        <h2 className="text-lg font-semibold text-surface-100">
          Library Scrape Status
        </h2>
        <p className="text-xs text-surface-500 mt-1">
          Metadata completeness by source. Eligible = outside 7-day cooldown.
        </p>
      </CardHeader>
      <CardContent className="space-y-6">
        {data?.sources.map((src) => (
          <div key={src.source} className="space-y-2">
            <h3 className="text-sm font-semibold text-surface-200">
              {SOURCE_LABELS[src.source] ?? src.source}
            </h3>
            <div className="space-y-1 text-sm">
              <StatusRow
                label="Matched"
                count={src.matched}
                color="text-emerald-400"
              />
              {src.notFound > 0 && (
                <StatusRow
                  label="Not found"
                  count={src.notFound}
                  eligible={src.notFoundEligible}
                  color="text-amber-400"
                  onAction={() =>
                    scrape.mutate({ source: src.source, status: "not_found" })
                  }
                  actionLabel="Retry now"
                  disabled={isActive || src.notFoundEligible === 0}
                />
              )}
              {src.error > 0 && (
                <StatusRow
                  label="Errors"
                  count={src.error}
                  eligible={src.errorEligible}
                  color="text-red-400"
                  onAction={() =>
                    scrape.mutate({ source: src.source, status: "error" })
                  }
                  actionLabel="Retry now"
                  disabled={isActive || src.errorEligible === 0}
                />
              )}
              {src.notAttempted > 0 && (
                <StatusRow
                  label="Not attempted"
                  count={src.notAttempted}
                  color="text-surface-400"
                  onAction={() =>
                    scrape.mutate({ source: src.source, status: "not_attempted" })
                  }
                  actionLabel="Scrape now"
                  disabled={isActive}
                />
              )}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function StatusRow({
  label,
  count,
  eligible,
  color,
  onAction,
  actionLabel,
  disabled,
}: {
  label: string;
  count: number;
  eligible?: number;
  color: string;
  onAction?: () => void;
  actionLabel?: string;
  disabled?: boolean;
}) {
  return (
    <div className="flex items-center justify-between">
      <span className={color}>
        {count.toLocaleString()} {label}
        {eligible !== undefined && eligible !== count && (
          <span className="text-surface-500 ml-1">
            ({eligible.toLocaleString()} eligible)
          </span>
        )}
      </span>
      {onAction && actionLabel && (
        <Button
          size="sm"
          variant="ghost"
          onClick={onAction}
          disabled={disabled}
          className="text-xs"
        >
          {actionLabel}
        </Button>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Add ScrapeStatusCard to the admin scan page**

In `web/src/pages/admin/scan-page.tsx`, import and render the card before the existing ScrapeCard:

```tsx
import { ScrapeStatusCard } from "@/features/admin/components/scrape-status-card";

// In the JSX, add before the existing scrape card:
<ScrapeStatusCard />
```

- [ ] **Step 4: Type check**

Run: `cd web && npx tsc --noEmit`
Expected: No errors.

- [ ] **Step 5: Commit**

```bash
git add web/src/features/admin/components/scrape-status-card.tsx web/src/pages/admin/scan-page.tsx web/src/hooks/use-admin.ts web/src/lib/api-routes.ts
git commit -m "feat: add library scrape status dashboard card to admin UI"
```

---

### Task 5: Full test suite verification

**Files:** None (verification only)

- [ ] **Step 1: Run Go tests**

Run: `cd server && go test ./... -v`
Expected: All pass.

- [ ] **Step 2: Run web tests**

Run: `cd web && npm run test`
Expected: All pass.

- [ ] **Step 3: Run player tests**

Run: `cd player && ./gradlew :shared:desktopTest`
Expected: All pass (no player changes but verify no regressions).
