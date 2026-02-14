# Spela - Project Conventions

## Overview
Spela is a self-hosted game emulation service with three components:
1. **Backend server** (Go) - Game hosting, user management, metadata, saves
2. **Web frontend** (React + TypeScript) - Server administration and game management
3. **Player app** (Kotlin Multiplatform + Compose Multiplatform) - Native game player

## Rules
1. **No web technology in the player app** - No HTML, CSS, or JavaScript. Must be fully native.
2. **Web UI uses React + TypeScript** - Vite build, Tailwind CSS, TanStack Query.
3. **Automated tests whenever reasonable** - For bugs: write failing test first, then fix.
4. **libretro only** - No custom emulation code. All emulation via libretro cores.
5. **A feature is not done until all tests pass** - Every change must have appropriate test coverage (E2E and/or unit tests), and the ENTIRE test suite must pass before a task is considered complete. No regressions allowed.
   - **Player app**: Espresso + Compose UI Test E2E tests (`player/run-e2e.sh` for Android, `player/run-desktop-tests.sh` for desktop) + unit tests. Any user-facing behavior change requires a corresponding E2E test. See `E2E.md` for device setup and test execution instructions. Prefer a physical device if connected (`adb devices`), fall back to emulator.
   - **Web frontend**: Playwright E2E tests + Vitest unit tests.
   - **Backend**: Go unit tests (`go test ./...`).
   - Run the full suite, not just the new tests. Catching regressions early is critical.

## Code Style

### Go (server/)
- Standard Go formatting (gofmt)
- Error handling: wrap errors with context using fmt.Errorf("doing thing: %w", err)
- Use structured logging (slog)
- Tests in _test.go files, use testify for assertions
- Table-driven tests preferred

### TypeScript/React (web/)
- Strict TypeScript, no `any` types
- Functional components only
- Named exports (no default exports)
- CSS via Tailwind utility classes
- Tests with Vitest + React Testing Library
- File naming: kebab-case for files, PascalCase for components

### Kotlin (player/)
- Follow Kotlin coding conventions
- Compose Multiplatform for all UI
- Clean Architecture: data → domain → presentation
- Unit tests with kotlin.test + JUnit5
- E2E tests with Espresso + Compose UI Test (see player/README.md for details)

## Data Persistence & User Preferences

### Server (Go)
All server-side data uses GORM with SQLite, auto-migrated on startup (`server/internal/db/database.go`).

**Per-user global data** (synced across all devices):
- `User` — profile (email, avatar) + emulation preferences (overlay, auto-save, auto-load, global default shader)
- `ConsoleShaderPreference` — per-console shader overrides `(user_id, console_id, shader)` with unique constraint on `(user_id, console_id)`
- `Favorite` — favorited games
- `PlayHistory` — last played timestamps and total play time per game
- `SaveState` — game save files (stored on filesystem, metadata in DB)

**Server-wide settings** (admin-only):
- `ServerSetting` — key-value table for `registration_enabled`, scraper credentials, etc.
- Endpoints: `GET/PUT /api/admin/settings`

**No per-device preference system on the server.** There is no device registration, device IDs in JWTs, or device-scoped settings table. All user data is global to the user account.

### Player App (Kotlin Multiplatform)
Local persistence uses **SQLDelight** (`player/shared/src/commonMain/sqldelight/com/spela/player/SpelaDatabase.sq`).

**SQLDelight tables:**
- `ServerConnectionEntity` — server URL, name, active flag
- `AuthTokenEntity` — JWT access/refresh tokens and expiration
- `CachedGameEntity` — game metadata cache (covers, descriptions)
- `DownloadEntity` — downloaded game tracking (local paths, sizes, timestamps)
- `PlayHistoryEntity` — local play history
- `ShaderOverrideEntity` — device-local per-console shader overrides (never synced to server)

**File-based storage** (`FileStorage` interface, platform implementations):
- `games/` — downloaded ROM files
- `cores/` — libretro core binaries
- `saves/` — game save states
- Android: `context.filesDir`; Desktop: OS-appropriate app data dir (macOS `~/Library/Application Support/Spela`, Linux `~/.local/share/spela`, Windows `%APPDATA%/Spela`)

**Emulation preferences** (per-user, synced via server):
- `showPerformanceOverlay`, `autoSaveEnabled`, `autoLoadSaveEnabled`
- `selectedShader` — global default shader (fallback when no per-console preference is set)
- `consoleShaders` — per-console shader preferences, synced across all devices
- Stored as fields on the `User` model and `ConsoleShaderPreference` table on the server, fetched via `GET/PUT /api/user/preferences`
- Player app uses `PreferencesRepository` to read/write; `SettingsViewModel` does optimistic updates with server sync
- Shader resolution order: device override → server per-console → server global default → NONE

### Scope Summary
| Data | Scope | Storage |
|------|-------|---------|
| Profile, favorites, play history | Per-user, all devices | Server DB |
| Save states | Per-user, per-game | Server filesystem + DB |
| Auth tokens, game cache, downloads | Per-device | Player SQLDelight DB |
| Server connections | Per-device | Player SQLDelight DB |
| Emulation toggles (overlay, auto-save) | Per-user, all devices | Server DB (`User` fields) |
| Shader global default | Per-user, all devices | Server DB (`User.SelectedShader`) |
| Shader per-console preferences | Per-user, all devices | Server DB (`ConsoleShaderPreference`) |
| Shader per-device overrides | Per-device | Player SQLDelight DB (`ShaderOverrideEntity`) |
| Server admin settings | Global | Server DB (`ServerSetting`) |

## Architecture Decisions
- See ARCHITECTURE.md for full technical architecture
- SQLite as default database (self-hosted friendly)
- JWT authentication with refresh token rotation
- REST API + WebSocket for real-time events
- libretro cores loaded dynamically per platform

## Git
- Conventional commits: feat:, fix:, docs:, test:, refactor:, chore:
- One logical change per commit
