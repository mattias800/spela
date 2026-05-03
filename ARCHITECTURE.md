# Spela - Self-Hosted Game Emulation Service

## Vision
A Steam-like experience for retro games. Host your game library on a server, manage it through a beautiful web UI, and play games on any device with cloud saves.

## System Components

### 1. Backend Server (Go)
- **Framework**: Gin (HTTP) + GORM (ORM)
- **Database**: SQLite (self-hosted friendly)
- **Auth**: JWT tokens with refresh token rotation
- **File Storage**: Local filesystem with configurable paths

#### Key Features
- Automatic game detection: scans configured directories for ROMs
- Identifies console/platform by file extension and directory structure
- Metadata scraping from three independent optional sources: libretro-thumbnails (box art), IGDB (titles, descriptions, ratings, screenshots), and SteamGridDB (hero banner artwork)
- User management with roles (owner, admin, user). First registered user becomes the owner; subsequent registrations are pending admin approval.
- Save state & save game sync per user per game
- Cover art, screenshots, and game info storage
- REST API for all operations
- WebSocket for real-time events (download progress, scan status)

#### Directory Structure Convention
```
/games/
├── nes/
│   ├── Super Mario Bros.nes
│   └── Zelda.nes
├── snes/
│   ├── Chrono Trigger.sfc
│   └── Final Fantasy VI.smc
├── gba/
│   └── Pokemon Emerald.gba
├── psx/
│   └── Final Fantasy VII/
│       ├── disc1.bin
│       └── disc1.cue
└── n64/
    └── Mario Kart 64.z64
```

#### API Endpoints

The server exposes ~150 endpoints under `/api/*` covering auth, games, consoles, saves, achievements, social (favorites, ratings, collections, activity feed), netplay sessions, shared sessions (relay turn-based multiplayer), challenges, admin operations (user management, library scan, scrape queue), BIOS upload, and the scrape pipeline.

The **live, complete spec** is served by the running server:

| Path | Format |
|------|--------|
| `/api/openapi` | OpenAPI 3.1 JSON |
| `/api/docs` | Swagger UI for interactive browsing |

For an offline copy, run `cd server && go run ./cmd/dump-openapi /tmp/openapi.json` (or `cd web && npm run openapi:dump` which writes to `web/src/generated/openapi.json`). The web client and Kotlin player client are generated from this dump — keeping the doc here in sync with the code is the build's responsibility, not this file's.

A handwritten endpoint list used to live here but drifted out of date. Don't rebuild it — point readers at `/api/docs`.

### 2. Web Frontend (React + TypeScript)
- **Build**: Vite
- **Styling**: Tailwind CSS + custom design system
- **State**: TanStack Query (React Query) for server state
- **Routing**: React Router v7
- **Icons**: Lucide React

#### Pages
- **Login/Register**: Clean, minimal auth screens
- **Dashboard**: Recently played, favorites, continue playing
- **Console Browser**: Grid of consoles with game counts and art
- **Game Browser**: Grid/list view with search, filters, sorting
- **Game Detail**: Cover art, screenshots, description, metadata, play history
- **Admin Panel**: User management, library scanning, metadata management
- **Settings**: Server configuration, directory paths, scraping settings
- **Metadata Fix**: Side-by-side comparison for fixing mismatched metadata

#### Design Principles
- Dark theme primary (with light mode option)
- Large cover art, visual-first design
- Minimal clicks to start playing
- Responsive layout (desktop + tablet)
- Smooth transitions and micro-animations
- Loading skeletons, not spinners

### 3. Player App (Kotlin Multiplatform + Compose Multiplatform)
- **UI**: Jetpack Compose Multiplatform (Android, Windows, macOS, Linux)
- **Emulation**: libretro via JNI/NDK
- **Networking**: Ktor HTTP client
- **Local Storage**: SQLDelight for local DB
- **DI**: Koin
- **Image Loading**: Coil (Compose multiplatform)

#### Architecture
- Clean Architecture with MVI (Model-View-Intent) pattern
- Shared KMP module for all business logic
- Platform-specific modules for libretro native integration
- Compose Multiplatform for unified UI across all platforms

#### Key Features
- Server connection management (multiple servers)
- Smooth authentication flow
- Game browsing with cover art
- Background download manager with notifications
- Automatic save state on exit
- Automatic save state load on start
- Controller detection and mapping (auto-detect common controllers)
- Performance overlay (FPS, frame time) - toggleable
- Fast forward, rewind support (where core supports it)
- Screenshot capture
- Local game cache management

#### libretro Integration
- Load cores dynamically (.so on Android/Linux, .dll on Windows, .dylib on macOS)
- Render to platform-native surface (SurfaceView on Android, OpenGL on desktop)
- Audio through platform audio APIs (Oboe on Android, OpenAL on desktop)
- Input mapping from platform controllers to libretro's input abstraction
- Single native bridge (`player/native/src/libretro_bridge.c`) handles all cores — per-core quirks are conditional branches, not separate implementations

##### Per-Core Graphics API Selection (Android)

The bridge tells cores which HW render context to prefer via `RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER`. This is configured per-core in `libretro_bridge.c` (`environment_callback`, case `RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER`):

| Core | Preferred API | Reason |
|------|--------------|--------|
| Dolphin (GameCube/Wii) `dolphin-emu` | Vulkan | Zero-copy compositing via VkImage, best performance |
| All other cores | GLES3 | Broad compatibility; DS, N64, PSX etc. work reliably with GLES |

On desktop, all cores prefer OpenGL Core.

Cores that only support one API will ignore this preference and request what they need via `SET_HW_RENDER` — the bridge accepts both Vulkan and GLES paths regardless of the preference.

**To change a core's preferred API**, modify the `GET_PREFERRED_HW_RENDER` handler in `libretro_bridge.c` and add a `strcmp` check for the core's `library_name`. The core name is available via `g_core.system_info.library_name` (populated by `retro_get_system_info` before callbacks are registered).

##### Per-Core Renderer Variables

Some cores have internal renderer selection via core variables (separate from the HW render preference above). These are set in the `SET_VARIABLES` handler in `libretro_bridge.c`:

| Core | Variable | Value | Reason |
|------|----------|-------|--------|
| mupen64plus_next (N64) | `mupen64plus-rdp-plugin` | `gliden64` (Android), `angrylion` (macOS) | GLES HW render on Android; software on macOS to avoid GL compositing issues |
| beetle_psx_hw (PS1) | `beetle_psx_hw_renderer` | `hardware_gl` (Android) | Avoids Granite Vulkan crashes on Adreno GPUs |
| Dolphin (GC/Wii) | `dolphin_cpu_thread` | `disabled` (Android) | Prevents deadlock with single-threaded libretro frontend |

### 4. Supported Platforms/Consoles

See the [Supported Consoles](README.md#supported-consoles) table in README.md for the full list (59 playable systems plus scaffolding for newer-gen consoles that aren't yet playable).

### 5. Console/Platform Mapping
Each console has:
- Name and abbreviation
- File extensions for detection
- Default libretro core
- Cover art aspect ratio (for proper display)
- Color theme (for UI accents)

### 6. Security Considerations
- All API endpoints require authentication (except login/register)
- Rate limiting on auth endpoints
- File downloads scoped to configured game directories (no path traversal)
- Admin role required for management operations
- HTTPS recommended (reverse proxy)
- CORS configuration for web frontend

## Tech Stack Summary
| Component | Technology |
|-----------|-----------|
| Backend | Go 1.25+, Gin, GORM, SQLite, Huma 2 (OpenAPI-driven handlers) |
| Web Frontend | React 19, TypeScript, Vite, Tailwind CSS, TanStack Query, React Router 7 |
| Player App | Kotlin Multiplatform, Compose Multiplatform, Ktor, SQLDelight, Koin, Coil |
| Emulation | libretro (cores loaded dynamically); EmulatorJS in the browser |
| Auth | JWT access tokens (1h) + rotating refresh tokens (90d) with token-family replay detection |
| Real-time | WebSocket (Hub for fan-out + per-session rooms for netplay relay) |

## Project Structure
```
spela/
├── server/                    # Go backend
│   ├── cmd/server/           # Entry point
│   ├── internal/
│   │   ├── api/              # HTTP handlers
│   │   ├── auth/             # Authentication
│   │   ├── db/               # Database models & migrations
│   │   ├── scanner/          # Game directory scanner
│   │   ├── scraper/          # Metadata scraper
│   │   ├── storage/          # File storage
│   │   └── websocket/        # WebSocket hub
│   ├── migrations/           # SQL migrations
│   └── go.mod
├── web/                       # React frontend
│   ├── src/
│   │   ├── components/       # Shared UI components
│   │   │   └── ui/           # Design system primitives
│   │   ├── features/         # Feature modules
│   │   ├── hooks/            # Custom hooks
│   │   ├── lib/              # Utilities
│   │   ├── pages/            # Route pages
│   │   └── types/            # TypeScript types
│   ├── public/
│   └── package.json
├── player/                    # Compose Multiplatform app
│   ├── shared/               # KMP shared module
│   │   ├── src/commonMain/   # Shared business logic + UI
│   │   ├── src/androidMain/  # Android-specific
│   │   └── src/desktopMain/  # Desktop-specific
│   ├── android/              # Android app module
│   ├── desktop/              # Desktop app module
│   └── build.gradle.kts
├── docs/                      # Documentation
│   ├── DEPLOY.md              # Production deployment guide
│   └── DEVELOPMENT.md         # Development setup guide
├── ARCHITECTURE.md            # This file
├── CONTRIBUTING.md            # Contribution guidelines
└── CLAUDE.md                  # Project conventions
```
