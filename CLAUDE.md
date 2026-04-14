# Spela - Project Conventions

> **Important:** Before starting any work, read `AGENTS.md` (agent workflow,
> design system enforcement) and `AGENT_TEAM.md` (team roles, design system
> principles, review checklist). These contain mandatory rules for UI work,
> component discipline, and the review process.
>
> For all UI work (web and player app), also read `DESIGN_IMPLEMENTATION.md`
> — the component hierarchy (Design → Content → Role), domain ownership rules,
> and the ongoing design system refactoring process.

## Overview
Spela is a self-hosted game emulation service with three components:
1. **Backend server** (Go) - Game hosting, user management, metadata, saves
2. **Web frontend** (React + TypeScript) - Server administration and game management
3. **Player app** (Kotlin Multiplatform + Compose Multiplatform) - Native game player

## One-time setup

After cloning the repo, point git at the tracked hooks directory so the
pre-push hook runs automatically on every push:

```sh
git config core.hooksPath .githooks
```

The pre-push hook runs `npm run build` for pushes that touch `web/` and
`go build ./...` for pushes that touch `server/`. Player-only pushes are
skipped. Bypass for a single push with `git push --no-verify`. See
`.githooks/pre-push` for the script.

## Rules
1. **No web technology in the player app** - No HTML, CSS, or JavaScript. Must be fully native.
2. **Web UI uses React + TypeScript** - Vite build, Tailwind CSS, TanStack Query.
3. **Automated tests whenever reasonable** - For bugs: write failing test first, then fix.
4. **libretro only** - No custom emulation code. All emulation via libretro cores.
6. **Idiomatic code** - Follow the conventions and patterns natural to each language (Go, TypeScript, Kotlin). Code should look like it was written by someone experienced in that ecosystem.
7. **Idempotent operations** - Operations that derive state (grouping, migrations, sync) should produce the same result regardless of starting state. `f(f(x)) = f(x)`. Derive from source data, not from potentially corrupted previous state.
5. **A feature is not done until all tests pass** - Every change must have appropriate test coverage (E2E and/or unit tests), and the ENTIRE test suite must pass before a task is considered complete. No regressions allowed.
   - **Player app**: See "Player App Testing Strategy" below for the desktop-primary / Android-smoke approach.
   - **Web frontend**: Playwright E2E tests + Vitest unit tests.
   - **Before running ANY E2E tests**, read [`docs/e2e-testing.md`](docs/e2e-testing.md) — covers all platforms (web, Android, desktop), common pitfalls, and the test tag convention.
   - **Backend**: Go unit tests (`go test ./...`).
   - Run the full suite, not just the new tests. Catching regressions early is critical.

### Player App Testing Strategy

The player app uses **Compose Multiplatform** — all UI composables, ViewModels, state, and navigation are 100% shared code in `player/shared/src/commonMain/`. This means the same composable renders identically on Android and desktop. **Do not duplicate the same UI assertions on both platforms.** Each platform's test suite has a distinct purpose:

**Desktop tests = Primary UI test suite** (`player/run-desktop-tests.sh`)
- Uses `SpelaTestHarness` with fake repositories — fast, no device/backend needed, CI-friendly.
- **This is where all feature-level UI tests go.** Every new screen, section, dialog, toggle, and interaction gets a desktop E2E test.
- Tests verify: composable rendering, state transitions, navigation, user interactions, empty states, error states, loading states.
- Run after every change. This is the fast feedback loop.

**Android tests = Integration smoke tests** (`player/run-e2e.sh`)
- Runs on a real device/emulator against the real backend (`docker-compose.e2e.yml`).
- **Do NOT mirror desktop UI tests.** Instead, focus on what desktop tests can't cover:
  - Real API round-trips (network, JSON serialization, auth token flow)
  - Login/session establishment end-to-end
  - Platform-specific behavior (touch input, keyboard dismiss, back gesture, Android lifecycle)
  - Smoke tests for critical flows (login → browse → game detail → play) to catch integration issues
- A smaller, focused set. Quality over quantity.

**When to write which:**
| What changed | Desktop test? | Android test? |
|---|---|---|
| New UI section/screen (shared composable) | Yes — thorough | No (unless it has platform-specific behavior) |
| New API integration (new endpoint wired up) | Yes (with fake repo) | Yes — smoke test with real API |
| Bug fix in shared UI logic | Yes — regression test | No |
| Platform-specific behavior (Android-only) | No | Yes |
| Critical user flow (login, play game) | Yes | Yes — smoke test |

**Rule of thumb:** If the code is in `commonMain/`, test it on desktop. If it touches platform-specific code or real network, add an Android smoke test. Never write the same assertion in both suites.

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
- **No negative margins** — never use `-mx-*`, `-mt-*`, etc. to break out of parent padding. Restructure the layout hierarchy so the component renders at the correct level instead.
- Tests with Vitest + React Testing Library
- File naming: kebab-case for files, PascalCase for components

### Kotlin (player/)
- Follow Kotlin coding conventions
- Compose Multiplatform for all UI
- Clean Architecture: data → domain → presentation
- Unit tests with kotlin.test + JUnit5
- Desktop E2E tests (primary): Compose UI Test + SpelaTestHarness with fake repos
- Android E2E tests (smoke): Espresso + Compose UI Test on real device/backend
- See "Player App Testing Strategy" above for what goes where

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

## Debugging Strategy

**Logging is the primary debugging tool** for issues that can't be reproduced in tests — especially platform-specific behavior on Android devices. When a bug isn't obvious from reading code:

1. **Add targeted diagnostic logging** (`println("[Tag] ...")` in Kotlin, `LOGI(...)` in native C) to trace the data flow through the pipeline. Use prefixed tags like `[DsTouch]`, `[Emulation]`, `[Download]` for easy filtering.
2. **Build, deploy, reproduce**, then pull logs with `adb logcat -d | grep "Tag"`.
3. **Analyze the logs** to identify where expected vs actual behavior diverge. Often the bug is in a different layer than suspected (e.g. core variables set correctly but a *different* variable controls the feature).
4. **Fix, remove diagnostic logs, commit.**

This is especially important for:
- **libretro core integration**: Core variables, input device mapping, HW rendering — the native C bridge (`player/native/src/`) mediates between Kotlin and the core. Logging at the JNI boundary reveals mismatches.
- **Android device-specific issues**: Secondary displays, touch input routing, lifecycle events — these can't be tested in desktop tests.
- **Network/download issues**: Progress reporting, file integrity, API response format mismatches.

Do not guess at the problem — instrument the code, read the logs, and let the data guide the fix.

## Architecture Decisions
- See ARCHITECTURE.md for full technical architecture
- See `player/LAYOUT.md` for the player app's shared layout composable system (SpScreen, SpMainContentPadding, SpSectionList, etc.). All screens must use these — no custom padding or scroll code.
- SQLite as default database (self-hosted friendly)
- JWT authentication with refresh token rotation
- REST API + WebSocket for real-time events
- libretro cores loaded dynamically per platform

## Git
- Conventional commits: feat:, fix:, docs:, test:, refactor:, chore:
- One logical change per commit
