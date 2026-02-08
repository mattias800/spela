# Spela

A self-hosted retro game emulation service. Host your ROM library on a server, manage it through a web UI, and play games on any device with cloud saves.

## Features

- **Automatic library scanning** -- point Spela at your ROM directories and it detects consoles, titles, and file types automatically
- **Metadata scraping** -- pulls cover art, descriptions, screenshots, and ratings from ScreenScraper
- **Cloud saves** -- save states sync per user per game, with automatic and manual save slots
- **Multi-user** -- JWT authentication with admin and user roles
- **Web dashboard** -- browse your library, manage metadata, view play history and favorites
- **Player app** -- Kotlin Multiplatform app with libretro integration for Android, Windows, macOS, and Linux
- **16 consoles supported** -- NES, SNES, Game Boy, GBA, N64, DS, Genesis, Master System, Saturn, PlayStation, PSP, Neo Geo, Arcade, TurboGrafx-16, Atari 2600

## Screenshots

<!-- TODO: Add screenshots of the web UI and player app -->

## Quick Start (Docker)

1. Create a directory for your games organized by console:

```
games/
  nes/
    Super Mario Bros.nes
  snes/
    Chrono Trigger.sfc
  gba/
    Pokemon Emerald.gba
```

2. Create a `.env` file:

```
SPELA_JWT_SECRET=your-secret-key-here
```

3. Run with Docker Compose:

```bash
docker compose up -d
```

4. Open `http://localhost:8080` and register your first user (the first registered user becomes admin).

5. Trigger a library scan from the admin panel to detect your games.

### Optional: ScreenScraper metadata

Add scraper credentials to your `.env` to enable automatic metadata fetching:

```
SPELA_SCRAPER_DEV_ID=your-dev-id
SPELA_SCRAPER_DEV_PASS=your-dev-password
SPELA_SCRAPER_USER=your-screenscraper-username
SPELA_SCRAPER_USER_PASS=your-screenscraper-password
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Go 1.22+, Gin, GORM, SQLite |
| Web Frontend | React 19, TypeScript, Vite, Tailwind CSS |
| Player App | Kotlin Multiplatform, Compose Multiplatform |
| Emulation | libretro (cores loaded dynamically) |
| Auth | JWT with refresh tokens |
| Real-time | WebSocket |

## Project Structure

```
spela/
  server/           Go backend (REST API, WebSocket, DB)
  web/              React web frontend
  player/           Compose Multiplatform player app
  ARCHITECTURE.md   Detailed architecture documentation
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for full system design, API endpoints, console mappings, and component details.

## Development

### Backend

```bash
cd server
go run ./cmd/server
```

See [server/README.md](server/README.md) for full setup instructions and environment variable reference.

### Web Frontend

```bash
cd web
npm install
npm run dev
```

### Player App

See [player/README.md](player/README.md) for build instructions.

## License

This project is for personal use. Do not distribute copyrighted ROM files.
