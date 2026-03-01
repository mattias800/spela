# Spela Server

Go backend for the Spela game emulation service. Provides a REST API for game library management, user authentication, save state sync, metadata scraping, and real-time events via WebSocket.

## Prerequisites

- Go 1.22+
- GCC (for SQLite CGO compilation)

## Setup

```bash
cd server
go mod download
go run ./cmd/server
```

The server starts on `http://localhost:8080` by default.

### Seed demo data

```bash
go run ./cmd/seed
```

Creates demo users (`admin`/`admin123`, `player`/`player123`), sample game entries, and core definitions.

### Docker

```bash
docker build -t spela-server .
docker run -p 8080:8080 -v ./games:/app/games:ro spela-server
```

Or use the project-level `docker-compose.yml`:

```bash
docker compose up -d
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPELA_PORT` | `8080` | HTTP listen port |
| `SPELA_DB_PATH` | `spela.db` | SQLite database file path |
| `SPELA_JWT_SECRET` | `change-me-in-production` | JWT signing secret (required in production) |
| `SPELA_GAME_DIRS` | `./games` | Comma-separated ROM directories to scan |
| `SPELA_SAVE_DIR` | `./saves` | Directory for save state files |
| `SPELA_CORE_DIR` | `./cores` | Directory for libretro core binaries |
| `SPELA_CORS_ORIGINS` | (none) | Comma-separated allowed CORS origins (same-origin only if unset) |
| `SPELA_WS_ORIGINS` | (any) | Comma-separated allowed WebSocket origins |
### Security notes

- `SPELA_JWT_SECRET` **must** be set in production. The server refuses to start in release mode (`GIN_MODE=release`) with the default secret.
- When `SPELA_CORS_ORIGINS` is set to `*`, `AllowCredentials` is automatically disabled to comply with the CORS specification.
- Set `SPELA_WS_ORIGINS` to restrict WebSocket connections to specific origins.

## API Overview

All endpoints except auth require a `Bearer` token in the `Authorization` header.

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login, returns access + refresh tokens |
| POST | `/api/auth/refresh` | Refresh access token |

### Games

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/games` | List games (paginated, filterable) |
| GET | `/api/games/:id` | Game details with metadata |
| GET | `/api/games/:id/download` | Download ROM file |
| POST | `/api/games/:id/metadata` | Update game metadata |
| POST | `/api/games/scan` | Trigger library scan |

Query params for `GET /api/games`: `search`, `consoleId`, `genre`, `sortBy` (title, created_at, file_size, rating), `sortOrder` (asc, desc), `page`, `pageSize`.

### Consoles

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/consoles` | List all consoles with game counts |
| GET | `/api/consoles/:id/games` | List games for a console |

### Save States

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/games/:id/saves` | List save states |
| POST | `/api/games/:id/saves` | Upload save state |
| GET | `/api/games/:id/saves/:saveId` | Download save state |
| DELETE | `/api/games/:id/saves/:saveId` | Delete save state |
| POST | `/api/games/:id/saves/auto` | Upload auto-save |
| GET | `/api/games/:id/saves/auto` | Get latest auto-save |

### Cores

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/cores` | List available libretro cores |
| GET | `/api/cores/:id/download` | Download core binary |
| GET | `/api/games/:id/core` | Get recommended core for a game |

### User

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/user/profile` | Current user profile |
| PUT | `/api/user/profile` | Update profile |
| GET | `/api/user/recent` | Recently played games |
| GET | `/api/user/favorites` | Favorite games |
| POST | `/api/user/favorites/:gameId` | Add favorite |
| DELETE | `/api/user/favorites/:gameId` | Remove favorite |

### Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/users` | List all users |
| PUT | `/api/admin/users/:id` | Update user role/email |
| GET | `/api/admin/settings` | Get server settings |
| PUT | `/api/admin/settings` | Update server settings |
| POST | `/api/admin/scrape` | Trigger metadata scrape |
| GET | `/api/admin/metadata-matches` | Review scraped/unscraped games |

### Other

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Health check (public, no auth) |
| GET | `/api/ws` | WebSocket for real-time events |

## Testing

```bash
go test ./...
```

## Project Layout

```
server/
  cmd/
    server/       Entry point
    seed/         Demo data seeder
  internal/
    api/          HTTP handlers, middleware, router, response DTOs
    auth/         JWT and password hashing
    db/           GORM models, migrations, console seeding
    scanner/      ROM directory scanner
    scraper/      Metadata scraper (LibRetro Thumbnails + IGDB)
    storage/      File storage (saves, cores)
    websocket/    WebSocket hub for real-time events
  Dockerfile      Multi-stage Alpine build
  go.mod
```
