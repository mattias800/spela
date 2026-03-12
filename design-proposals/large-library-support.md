# Large Library Support

## Problem Statement

When a user adds a complete no-intro ROM collection (all verified), the UI becomes
unusable — filled with duplicates, betas, prototypes, regional variants, and thousands
of entries per console. The system was designed for small curated libraries and needs
to scale gracefully to complete collections.

## Core Problem

The scanner's `GameTitle()` strips all parenthesized tags, so `Super Mario World (USA)`,
`(Europe)`, `(Japan)`, `(Rev 1)` all become identical "Super Mario World" cards. A no-intro
SNES set produces 3000+ entries with massive duplication.

## Existing Building Blocks (unused during scan)

- `ExtractRegion()` in `scraper/region.go` — parses region tags from filenames
- `computePriority()` in `scraper/namematch.go` — ranks variants (clean > revision > beta/proto)
- `hasPreferredRegion()` in `scraper/namematch.go` — prefers USA/World
- DAT file parsing in `scraper/datcache.go` — CRC verification and canonical names
- `Region` field on the `Game` model — exists but only populated by scraper, not scanner

## What Breaks at Scale

| Issue | Severity | Where |
|---|---|---|
| All variants show identical title + cover art | Critical | Both platforms |
| No region/status filters exist | Critical | Both platforms |
| No way to hide betas, protos, samples | Critical | Both platforms |
| Pagination renders all 63+ page buttons | High | Web: `pagination.tsx` |
| Player app loads ALL games into memory per console | High | Player: `ConsoleScreen.kt` |
| Auto-scrape fires 48 simultaneous requests per page | High | Both platforms |
| No alphabet quick-jump for sorted lists | Medium | Both platforms |
| No compact/table view for dense browsing | Medium | Web |

---

## Implementation Phases

### Phase 1 — Foundation (Backend)

Parse no-intro tags at scan time and enable variant grouping in the API.

**Changes:**

1. **Parse filename metadata during scan** — Extract region, revision, and tags
   (beta/proto/sample/demo/unlicensed) from filenames when creating Game records.
   Use the existing `ExtractRegion()` function. Add new fields to the Game model:
   - `Region` — populated at scan time (not just scrape time)
   - `Tags` — comma-separated: beta, proto, sample, demo, unlicensed, hack, etc.
   - `Revision` — parsed from `(Rev X)`, `(v1.1)`, etc.
   - `IsPreRelease` — true for beta/proto/sample/demo
   - `GroupKey` — normalized title for variant grouping (console-scoped)

2. **Variant grouping** — Add `IsPrimary bool` and `PrimaryGameID *uint` to Game.
   Post-scan step groups games by `(console_id, group_key)` and elects a primary
   variant per group using: user region preference > latest revision > has metadata >
   verified > shortest filename.

3. **API changes** — `GET /api/games` and `GET /api/consoles/:id/games` return only
   primary variants by default. New query parameters:
   - `grouped=false` — show all variants
   - `region=USA,Europe` — filter by region
   - `hidePreRelease=true` (default) — hide betas/protos/samples
   - Response includes `variantCount` on each game when grouped

4. **Database indexes** — Add indexes on `(console_id, group_key)`,
   `(console_id, is_primary)`, `region`, `is_pre_release`.

5. **Backfill migration** — Existing games get their fields populated on startup.

### Phase 2 — Web UI Filters and Fixes

Surface the new backend capabilities in the web frontend.

**Changes:**

1. **Region filter** — Chip picker in the advanced filter panel, populated from
   distinct regions in the library.

2. **"Hide betas & protos" toggle** — Prominent toggle in the filter bar (not
   buried in advanced panel). Default ON.

3. **Variant badge on game cards** — When a game has variants, show "N versions"
   badge. Clicking goes to game detail where variants are listed.

4. **Fix pagination** — Ellipsis-style truncation (1 2 3 ... 61 62 63) instead
   of rendering all page buttons.

5. **Active filter pills** — Show active filters as dismissible chips above the
   game grid, visible without opening the advanced panel.

6. **"Best versions only" preset** — One-click button that applies region +
   release-only + verified filters. Prominently placed.

### Phase 3 — Player App Updates

Bring the player app up to parity with the web for large libraries.

**Changes:**

1. **Server-side pagination** — `GameListViewModel` fetches pages incrementally
   instead of loading all games at once. Infinite scroll with page loading.

2. **Region filter chips** — Horizontal chip strip for region filtering.

3. **"Hide betas" toggle** — In library controls, default ON.

4. **Variant count on game cards** — Show "N versions" indicator.

5. **Game detail variants section** — List all variants with region, revision,
   verification status, and file size. User can play any variant.

### Phase 4 — User Preferences and Smart Defaults

Personalization and polish.

**Changes:**

1. **User region preference** — Ordered list of preferred regions (e.g., [USA,
   Europe, World]). Stored on server, synced across devices. Used by variant
   grouping to pick the representative.

2. **Admin default settings** — Server-wide defaults for hide pre-release,
   default region, etc.

3. **Alphabet quick-jump** — A-Z sidebar on game grids (web and player).
   Clicking jumps to first game starting with that letter. Dim unavailable letters.

4. **Smart scraping** — Scrape one game per variant group, propagate metadata
   to all variants. Skip pre-release games by default. Prioritize games users
   have interacted with.

5. **Compact grid / table view** — Denser view modes for power users on web.

### Phase 5 — Polish and Power Features

Nice-to-have improvements.

**Changes:**

1. **Per-user game hiding** — "Hide from library" action on game cards and detail
   page. Hidden games excluded by default, accessible via "Show hidden" toggle.

2. **Admin bulk hide by pattern** — Pattern-based hide rules (all betas, all
   Japanese-only, etc.).

3. **Metadata coverage dashboard** — Admin page showing scrape coverage, missing
   covers, failure rates by console.

4. **"Group by" support** — Group game grid by genre, year, or first letter.

5. **DAT file import** — Upload no-intro DAT files for batch CRC verification
   and canonical name population.

---

## Key Architecture Decisions

### Variant Grouping: IsPrimary flag on Game model

Add `IsPrimary bool` and `PrimaryGameID *uint` to the existing Game table rather
than creating a separate GameGroup table. This is the minimal schema change:
- Existing queries add `WHERE is_primary = true` for default behavior
- `?grouped=false` removes that filter for power users
- Self-referential relationship within the same table
- GORM auto-migration handles the new columns

### GroupKey Computation

`GroupKey = normalize(title) + console_id` where normalize:
- Strips all parenthesized and bracketed tags (same as current `GameTitle()`)
- Lowercases
- Strips articles ("The", "A", "An")
- Strips accented characters
- Must match the scraper's `normalizeName()` for consistency

### Primary Variant Election

Priority order for selecting the "best" variant in a group:
1. User's preferred region (if set)
2. Server default region (if set), fallback to USA > World > Europe
3. Latest revision (Rev B > Rev A > no Rev)
4. Has IGDB metadata / cover art
5. CRC verified
6. Not pre-release
7. Shortest filename (simplest name = cleanest dump)

### Backward Compatibility

- All changes are additive — no data is lost
- Existing API behavior preserved with `grouped=false`
- GroupKey and IsPrimary are computed on startup for existing games
- No breaking changes to the Game response schema (new fields only)
