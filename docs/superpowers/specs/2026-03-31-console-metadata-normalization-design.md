# Console Metadata Normalization

## Goal

Move console metadata (manufacturer, release year, media type, units sold, summary) from hardcoded player app data into the server database with proper normalized schema. Add a `code` column to consoles for stable URL/API identifiers separate from display abbreviations.

## Current State

- Console technical data (cores, extensions, generation) lives in the server DB
- Rich metadata (manufacturer, release year, media type, units sold, summary) is hardcoded in `player/shared/.../ConsoleMetadata.kt`
- Web frontend has no access to this metadata
- No manufacturer/maker concept in the database
- Console lookup uses `abbreviation` column for both display and URL identifiers

## Database Schema

### New table: `media_type_categories`

| Column | Type | Constraints |
|--------|------|-------------|
| id | uint | PK, auto-increment |
| code | string | unique, not null |
| name | string | not null |

Seed data: `cartridge`, `disc`, `digital`, `board`

### New table: `media_types`

| Column | Type | Constraints |
|--------|------|-------------|
| id | uint | PK, auto-increment |
| code | string | unique, not null |
| name | string | not null |
| category_id | uint | FK → media_type_categories.id, not null |

Seed data: `cartridge`, `game-card`, `cd-rom`, `gd-rom`, `dvd-rom`, `blu-ray`, `umd`, `digital`, `arcade-board`, `floppy-disk`

### New table: `hardware_makers`

| Column | Type | Constraints |
|--------|------|-------------|
| id | uint | PK, auto-increment |
| code | string | unique, not null |
| name | string | not null |
| created_at | timestamp | |
| updated_at | timestamp | |

Seed data: ~15 rows (nintendo, sega, sony, microsoft, atari, nec, snk, philips, commodore, etc.)

### Existing table: `consoles` — new columns

| Column | Type | Constraints |
|--------|------|-------------|
| code | string | unique, not null |
| hardware_maker_id | uint | FK → hardware_makers.id, nullable |
| media_type_id | uint | FK → media_types.id, nullable |
| release_year | int | nullable |
| units_sold | bigint | nullable (raw number, e.g. 61900000) |
| summary | text | nullable |

### Relationships

- Console → HardwareMaker: many-to-one (nullable). Null for arcade, demo categories.
- Console → MediaType: many-to-one (nullable).
- HardwareMaker → Consoles: one-to-many.
- MediaType → MediaTypeCategory: many-to-one (not null).

## API Changes

### Updated: `GET /api/consoles` and `GET /api/consoles/:code`

Console responses include new nested fields:

```json
{
  "id": "nes",
  "code": "nes",
  "name": "Nintendo Entertainment System",
  "abbreviation": "NES",
  "maker": {
    "code": "nintendo",
    "name": "Nintendo"
  },
  "mediaType": {
    "code": "cartridge",
    "name": "Cartridge",
    "category": {
      "code": "cartridge",
      "name": "Cartridge"
    }
  },
  "releaseYear": 1983,
  "unitsSold": 61900000,
  "summary": "The Nintendo Entertainment System...",
  "gameCount": 58,
  ...existing fields...
}
```

Nullable fields return `null` when not set.

Console lookup switches from `WHERE abbreviation = ?` to `WHERE code = ?`. URL paths stay the same visually since code values match current abbreviation values (lowercase).

### New: `GET /api/makers`

List all hardware makers with console counts:

```json
[
  { "code": "nintendo", "name": "Nintendo", "consoleCount": 8 },
  { "code": "sega", "name": "Sega", "consoleCount": 5 }
]
```

### New: `GET /api/makers/:code`

Single maker with its consoles:

```json
{
  "code": "nintendo",
  "name": "Nintendo",
  "consoles": [ ...ConsoleResponse array... ]
}
```

No separate endpoints for `media_types` or `media_type_categories` — they are reference data exposed inline via console responses.

## Seed Data

The seed script populates all reference tables and updates consoles with metadata. The data is static in the seed script — not fetched from IGDB at runtime.

Summaries are sourced from IGDB's `/platforms` endpoint during development and hardcoded into the seed. This avoids per-install IGDB API calls.

### Seed execution order

1. Seed `media_type_categories` (4 rows)
2. Seed `media_types` (~10 rows, references categories)
3. Seed `hardware_makers` (~15 rows)
4. Update `consoles` with `code`, `hardware_maker_id`, `media_type_id`, `release_year`, `units_sold`, `summary`

All seed operations are idempotent (upsert by code).

## Migration Strategy

GORM auto-migrates new columns and tables on startup. No manual migration needed. Existing data (games, saves, user data) is unaffected — only new columns are added to the consoles table.

The `code` column is populated during seeding with the same values as the current `abbreviation` column (lowercase). After migration, API/URL lookups use `code` instead of `abbreviation`.

## Frontend Changes

### Web

- Console detail page: show maker name, release year, media type, units sold, and summary in the hero banner info section
- Console list page: optionally group or filter by maker
- TypeScript `Console` type updated with new fields

### Player App

- `Console` data class updated with new API fields (maker, mediaType, releaseYear, unitsSold, summary)
- Remove hardcoded `ConsoleMetadata.kt` (`getConsoleInfo()` function and `ConsoleInfo` data class)
- Console detail screen reads metadata from the API response instead of local hardcoded data
- Console list card info row uses API data (currently hardcoded manufacturer + year)

## Testing

- Go unit tests: seed functions, API response shape, maker endpoints
- Web: update existing console detail page tests for new banner content
- Player: update tests that reference `ConsoleMetadata.kt`

## Not in scope

- IGDB runtime fetching (future enhancement if seed data drifts)
- Maker logo/image assets
- Console hardware specs (CPU, RAM, graphics — could be added later)
- Renaming `abbreviation` column (it stays as the display label)
