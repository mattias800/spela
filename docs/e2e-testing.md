# Web E2E Testing Guide

This document explains how to run the Playwright E2E tests for the web frontend
reliably, what the infrastructure does, and how to troubleshoot common issues.

## Quick Start

From the repository root:

```bash
# Run all E2E tests (builds server, seeds DB, starts everything, runs tests)
./run-e2e.sh

# Run a specific test file
./run-e2e.sh admin-settings

# Run tests matching a pattern
./run-e2e.sh "registration"

# Open Playwright UI mode (interactive)
./run-e2e.sh --ui

# Run with visible browser
./run-e2e.sh --headed

# Skip Go build (reuse cached binary from previous run)
SKIP_BUILD=1 ./run-e2e.sh
```

That's it. The script handles everything.

## What `run-e2e.sh` Does

The script performs 8 steps in order:

| Step | What happens |
|------|-------------|
| 1. Clear ports | Kills any existing processes on ports 8080 (server) and 5173 (Vite) |
| 2. npm deps | Installs `node_modules` and Playwright browsers if missing |
| 3. Clean auth | Removes stale `storage-state.json` from previous runs |
| 4. Build & seed | Compiles the Go server, creates a fresh SQLite DB in a temp dir, seeds consoles/cores/users |
| 5. Start server | Launches the server with `SPELA_TEST_MODE=true` and waits for the health check |
| 6. Game scan | Triggers a library scan and **polls the scan status endpoint until complete**, then verifies games exist |
| 7. Start Vite | Starts the Vite dev server and waits for it to respond |
| 8. Run tests | Executes `npx playwright test` with any arguments you passed |

On exit (success, failure, or Ctrl+C), the script kills the server and Vite
processes and deletes the temp directory.

## Prerequisites

- **Go** (1.21+) with CGO enabled (for SQLite)
- **Node.js** (18+) and npm
- **Python 3** (used by the script to parse JSON responses)
- **curl** and **lsof** (standard on macOS/Linux)

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SKIP_BUILD` | unset | Set to `1` to skip the Go build and reuse cached binaries from `.e2e-cache/` |
| `SKIP_INSTALL` | unset | Set to `1` to skip the npm dependency check |
| `E2E_SERVERS_RUNNING` | unset | Set automatically by `run-e2e.sh` to tell Playwright not to start its own webServer |

## Architecture

### Fresh state every run

Each run creates a **completely fresh temp directory** with a new SQLite database.
There is no persistent state between runs. This eliminates an entire class of
flakiness caused by leftover data.

```
/tmp/xxx-e2e/
  spela.db      # fresh database, seeded with consoles + cores + admin/player users
  saves/        # empty
  cores/        # empty
  images/       # empty
  bios/         # empty
  dats/         # empty
```

### Test isolation within a run

Tests share a single server instance (Playwright runs with `workers: 1`).
Between tests, the `resetServer()` function calls `POST /api/test/reset` which:

1. Deletes all user-generated data (sessions, saves, collections, challenges, etc.)
2. Preserves consoles, cores, and scanned games
3. Resets the `admin` and `player` users to their default state

Tests that mutate server state should call `resetServer()` in `beforeEach` or `beforeAll`:

```typescript
import { test, expect, resetServer } from "./fixtures";

test.describe("My Feature", () => {
  test.beforeEach(async () => {
    await resetServer();
  });

  test("does something", async ({ page }) => {
    // ...
  });
});
```

### Readiness gate

Before any tests run, the global setup (`global-setup.spec.ts`) verifies:

1. Server health check passes
2. Test reset endpoint is available (confirms `SPELA_TEST_MODE=true`)
3. Admin login works
4. Games have been scanned (game count > 0)

If any of these fail, all tests are skipped with a clear error message.

### Authentication

The global setup logs in as `admin/admin123` and saves the browser storage state
to `e2e/.auth/storage-state.json`. All subsequent tests inherit this
authenticated context automatically.

Tests that need an unauthenticated context can override this:

```typescript
test.use({ storageState: { cookies: [], origins: [] } });
```

## Test Patterns

### Tests that hit the real server

These tests exercise the actual API. Use `resetServer()` to ensure clean state:

```typescript
test.beforeEach(async () => {
  await resetServer();
});

test("admin can change settings", async ({ page }) => {
  await page.goto("/admin/settings");
  // interact with real server...
});
```

### Tests with mocked API routes

Many tests mock API responses using Playwright's `page.route()`. These don't
need `resetServer()` since they never hit the real server:

```typescript
test("shows challenge cards", async ({ page }) => {
  await page.route("**/api/challenges?*", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [...], total: 1, page: 1, pageSize: 20 }),
    });
  });

  await page.goto("/challenges");
  await expect(page.getByText("My Challenge")).toBeVisible();
});
```

## Seeded Test Data

The E2E environment provides:

| Data | Details |
|------|---------|
| **Admin user** | `admin` / `admin123` (role: `owner`) |
| **Player user** | `player` / `player123` (role: `user`) |
| **Consoles** | ~60 systems (NES, SNES, GBA, PSX, etc.) |
| **Cores** | All libretro core definitions |
| **Games** | ~171 games scanned from `testdata/roms/` |

## Alternative: Manual Setup

If you want to start the environment manually (e.g., to keep it running while
you iterate on tests):

```bash
# Terminal 1: Start the E2E environment
bash web/e2e/start-e2e.sh

# Terminal 2: Run tests (will reuse the running server)
cd web
npx playwright test
npx playwright test --ui          # interactive mode
npx playwright test admin-settings  # specific file
```

The `start-e2e.sh` script performs the same setup as `run-e2e.sh` steps 1-7
and then blocks until Ctrl+C.

## Alternative: Docker Compose

For CI or when you don't want to build locally:

```bash
docker compose -f docker-compose.e2e.yml up -d --build --wait
cd web
npx playwright test
docker compose -f docker-compose.e2e.yml down -v  # -v removes volumes
```

Note: Docker volumes persist between runs. Always use `down -v` to get a clean
state, or rely on `resetServer()` within tests.

## Troubleshooting

### "Server did not become ready"

- Check if something else is using port 8080: `lsof -i :8080`
- The script should kill it automatically, but if `lsof` fails (permissions),
  you may need to kill it manually

### "No games found after scan"

- Verify `testdata/roms/` exists and contains ROM files
- Check server logs for scan errors (the scan runs async and the script polls
  for completion)

### "Test reset failed"

- The server must be started with `SPELA_TEST_MODE=true` for the reset endpoint
  to be registered

### Stale auth / "401 Unauthorized" in tests

- `run-e2e.sh` automatically removes old `storage-state.json`
- If running manually, delete `web/e2e/.auth/storage-state.json`

### Port already in use after a crash

```bash
# Kill processes on E2E ports
kill $(lsof -ti :8080) 2>/dev/null
kill $(lsof -ti :5173) 2>/dev/null
```

### Tests pass locally but fail in CI

- CI does not currently run Playwright E2E tests (only unit tests and desktop
  player tests). See `.github/workflows/ci.yml` for the current CI setup.

## File Reference

| File | Purpose |
|------|---------|
| `run-e2e.sh` | One-command test runner (recommended entry point) |
| `web/e2e/start-e2e.sh` | Starts E2E environment only (no test execution) |
| `web/run-e2e.sh` | Thin wrapper around `npx playwright test` |
| `web/playwright.config.ts` | Playwright configuration (ports, workers, auth) |
| `web/e2e/fixtures.ts` | Exports `test`, `expect`, and `resetServer()` |
| `web/e2e/global-setup.spec.ts` | Readiness checks + auth setup |
| `web/e2e/*.spec.ts` | Individual test files |
| `docker-compose.e2e.yml` | Docker-based E2E environment |
| `server/internal/api/test_handler.go` | Server-side reset endpoint |
| `server/cmd/seed/main.go` | Database seeding logic |
| `testdata/roms/` | Test ROM files (not in git) |
