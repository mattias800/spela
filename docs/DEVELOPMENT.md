# Development Guide

This guide covers building Spela from source and running it locally for development.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Go | 1.25+ | Backend server (production Dockerfile pins `golang:1.25-alpine`) |
| Node.js | 20+ | Web frontend |
| JDK | 17+ | Player app (Kotlin Multiplatform; `jvmTarget = 17`) |
| Docker | Latest | E2E test environment, deployment |

## Quick Start

### Backend

```bash
cd server
go run ./cmd/server
```

The server starts on `http://localhost:8080` with an auto-migrated SQLite database.

### Web Frontend

```bash
cd web
npm install
npm run dev
```

The Vite dev server starts on `http://localhost:5173` and proxies API requests to the backend.

### Player App

See [player/README.md](../player/README.md) for build instructions. The player app is a Kotlin Multiplatform project targeting Android, Windows, macOS, and Linux.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Go, Gin, GORM, SQLite |
| Web Frontend | React, TypeScript, Vite, Tailwind CSS |
| Player App | Kotlin Multiplatform, Compose Multiplatform, libretro |
| Real-Time | WebSocket |
| Auth | JWT with refresh token rotation |
| TURN Server | coturn (for netplay NAT traversal) |

## Architecture

See [ARCHITECTURE.md](../ARCHITECTURE.md) for the full system design, API endpoints, and component details.

## Project Structure

```
spela/
├── server/          # Go backend (see server/README.md)
├── web/             # React frontend (see web/README.md)
├── player/          # Compose Multiplatform app (see player/README.md)
├── docs/            # Deployment and documentation
└── docker-compose.yml
```

## Testing

See [TESTING.md](../TESTING.md) for the full testing guide. Quick reference:

| Suite | Command |
|-------|---------|
| Go backend | `cd server && go test ./...` |
| Web unit tests | `cd web && npx vitest run` |
| Web E2E tests | `cd web && npx playwright test` (requires Docker E2E env) |
| Player desktop tests | `cd player && ./run-desktop-tests.sh` |
| Player Android E2E | `cd player && ./run-e2e.sh` (requires device) |

## Useful Links

- [ARCHITECTURE.md](../ARCHITECTURE.md) — System design and API endpoints
- [TESTING.md](../TESTING.md) — Full testing guide
- [E2E.md](../E2E.md) — Android E2E test setup
- [DEPLOY.md](DEPLOY.md) — Production deployment
- [CONTRIBUTING.md](../CONTRIBUTING.md) — Code style and PR guidelines
