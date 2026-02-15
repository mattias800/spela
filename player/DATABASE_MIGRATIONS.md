# Database Migration Strategy for Spela Player

## Overview

The Spela player app uses SQLDelight 2.x for local data persistence. This document explains how database migrations work so users can upgrade without losing data.

## How It Works

### Schema Versioning

SQLDelight tracks schema versions automatically:
- No `.sqm` files = version 1 (current state)
- Adding `1.sqm` bumps version to 2
- Adding `2.sqm` bumps version to 3, etc.

The current version is available at `SpelaDatabase.Schema.version`.

### Platform Behavior

**Android**: `AndroidSqliteDriver` handles create and migrate automatically — it compares the DB's internal version against `SpelaDatabase.Schema.version` and runs any needed `.sqm` migrations.

**Desktop**: `JdbcSqliteDriver` requires manual version tracking via SQLite's `PRAGMA user_version`. The desktop platform module reads the current version, calls `create()` for fresh databases or `migrate()` for upgrades, then updates `user_version`.

## Adding a Migration

When changing the schema:

1. **Update `SpelaDatabase.sq`** with the new table/column definitions and queries
2. **Create a migration file** at `shared/src/commonMain/sqldelight/com/spela/player/migrations/{version}.sqm`
   - `1.sqm` migrates from version 1 → 2
   - `2.sqm` migrates from version 2 → 3
3. **Write the migration SQL** (ALTER TABLE, CREATE TABLE, etc.)
4. **Build and test** — `verifyMigrations` is enabled in `build.gradle.kts` and will fail the build if the migration doesn't produce the same schema as `SpelaDatabase.sq`

### Example

To add a `notes` column to `CachedGameEntity`:

**Update `SpelaDatabase.sq`:**
```sql
CREATE TABLE CachedGameEntity (
    ...
    notes TEXT,   -- new column
    cached_at INTEGER NOT NULL
);
```

**Create `migrations/1.sqm`:**
```sql
ALTER TABLE CachedGameEntity ADD COLUMN notes TEXT;
```

## Best Practices

- **Never delete old migration files** — users upgrading from old versions need the full chain
- **Use DEFAULT values** for new NOT NULL columns (e.g., `ADD COLUMN foo INTEGER NOT NULL DEFAULT 0`)
- **Prefer additive changes** — adding columns/tables is safe; renaming or removing is risky
- **Test multi-step upgrades** — verify that v1 → v3 works (not just v2 → v3)

## Testing Migrations

1. Build and run the current version, generate local data
2. Apply your schema changes and migration files
3. Build and run the new version over the existing database
4. Verify: app starts, all data is preserved, new features work
