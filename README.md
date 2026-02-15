<p align="center">
  <h1 align="center">Spela</h1>
  <p align="center"><em>Nu spelar vi!</em> — Your retro game library, self-hosted. Play anywhere.</p>
</p>

<p align="center">
  <a href="#supported-consoles">16 Consoles</a> &bull;
  <a href="#features">Features</a> &bull;
  <a href="#quick-start">Quick Start</a> &bull;
  <a href="docs/DEPLOY.md">Deploy Guide</a>
</p>

---

Spela is a self-hosted retro game emulation platform. Point it at your ROM collection, and it turns into a beautiful game library with cover art, metadata, cloud saves, multiplayer, and achievements -- accessible from any device.

Think of it as your own personal Steam for retro games.

<p align="center">
  <img src="docs/screenshots/dashboard.png" alt="Dashboard with game library" width="720" />
</p>

<p align="center">
  <img src="docs/screenshots/games.png" alt="SNES games with cover art" width="355" />
  <img src="docs/screenshots/game-detail.png" alt="Game detail page" width="355" />
</p>
<p align="center">
  <img src="docs/screenshots/library.png" alt="Console browser" width="720" />
</p>

## Why Spela?

- **One library, every device** -- your games, saves, and progress sync across Android, Windows, macOS, and Linux
- **Play in seconds** -- browse your collection with cover art, pick a game, and you're playing. No fiddling with cores or configs
- **Self-hosted** -- your server, your data. Runs on a Raspberry Pi, a NAS, or a VPS. No cloud dependency
- **Multiplayer** -- play with friends via real-time netplay or async turn-based relay sessions
- **Beautiful** -- dark-themed UI designed for browsing large game libraries visually

## Features

### Play Your Way

| Feature | Description |
|---------|-------------|
| **Native Player App** | Kotlin Multiplatform app for Android, Windows, macOS, and Linux with libretro emulation |
| **Browser Play** | Play directly in the web browser via EmulatorJS -- no install needed |
| **Cloud Saves** | Save states sync automatically across all your devices |
| **Shared Saves** | Share your save states publicly for others to download |
| **Auto-Save & Auto-Load** | Never lose progress -- games auto-save on exit and auto-load on start |
| **Visual Shaders** | CRT scanlines, LCD grid, sharp bilinear, and more -- per-console or global |
| **Gamepad Support** | Auto-detected controllers with customizable button mapping, per-console overrides |
| **Fast Forward** | Speed through slow sections with a button press |
| **Screenshots** | Capture in-game screenshots at any time |
| **Performance Overlay** | Optional FPS and frame time display |

### Multiplayer

| Feature | Description |
|---------|-------------|
| **Real-Time Netplay** | Two-player online multiplayer with lockstep input sync. Share a 6-character invite code and play together |
| **Relay Sessions** | Asynchronous turn-based multiplayer -- take turns playing and pass the save to your friend |
| **Relay Invitations** | Invite friends to join relay sessions with turn management and timeout protection |

### Social

| Feature | Description |
|---------|-------------|
| **Activity Feed** | See what your friends are playing, their new favorites, ratings, and achievements |
| **Ratings & Reviews** | Rate games and write reviews. See community averages |
| **Favorites** | Mark your favorite games, synced across devices |
| **Play Later Queue** | Save games for later with custom ordering |
| **Collections** | Create named game lists (public or private) |
| **Play History** | Track time played per game with most-played leaderboards |
| **Online Status** | See who's currently playing and what they're playing |
| **User Profiles** | Public profiles with gaming activity and stats |

### Library Management

| Feature | Description |
|---------|-------------|
| **Auto-Detection** | Point Spela at your ROM folders -- it detects consoles, titles, and file types automatically |
| **Metadata Scraping** | Cover art, descriptions, screenshots, ratings, and developer info pulled from ScreenScraper |
| **Multi-User** | Multiple accounts with admin and user roles. First registered user becomes admin |
| **RetroAchievements** | Link your RetroAchievements account for in-game achievements with progress tracking |
| **Multi-Server** | The player app can connect to multiple Spela servers |

## Supported Consoles

| Console | Core | Extensions |
|---------|------|------------|
| NES | Nestopia | `.nes` `.fds` |
| SNES | Snes9x | `.sfc` `.smc` |
| Game Boy | Gambatte | `.gb` |
| Game Boy Color | Gambatte | `.gbc` |
| Game Boy Advance | mGBA | `.gba` |
| Nintendo 64 | Mupen64Plus | `.n64` `.z64` `.v64` |
| Nintendo DS | DeSmuME | `.nds` |
| Sega Master System | Genesis Plus GX | `.sms` |
| Sega Genesis / Mega Drive | Genesis Plus GX | `.md` `.gen` `.bin` |
| Sega Saturn | Beetle Saturn | `.iso` `.bin/.cue` |
| PlayStation | Beetle PSX | `.bin/.cue` `.iso` `.pbp` |
| PSP | PPSSPP | `.iso` `.cso` |
| Neo Geo | FBNeo | `.zip` |
| Arcade (MAME) | MAME 2003+ | `.zip` |
| TurboGrafx-16 | Beetle PCE | `.pce` |
| Atari 2600 | Stella | `.a26` `.bin` |

## Quick Start

The fastest way to get running is Docker Compose.

### 1. Organize your ROMs

```
games/
  nes/
    Super Mario Bros.nes
  snes/
    Chrono Trigger.sfc
  gba/
    Pokemon Emerald.gba
```

### 2. Generate secrets and configure

```bash
./generate-secrets.sh
```

Edit the generated `.env` file and set `SPELA_GAMES_PATH` to your ROM directory.

### 3. Start Spela

```bash
docker compose up -d
```

### 4. Open the web UI

Go to `http://localhost:8080`, register your first account (it becomes admin), and trigger a library scan from the admin panel.

### 5. Download the player app

Download the player app on your device, point it at your server, and start playing.

---

For production deployment with HTTPS, TURN server, and Portainer, see the **[Deploy Guide](docs/DEPLOY.md)**.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Go, Gin, GORM, SQLite |
| Web Frontend | React, TypeScript, Vite, Tailwind CSS |
| Player App | Kotlin Multiplatform, Compose Multiplatform, libretro |
| Real-Time | WebSocket |
| Auth | JWT with refresh token rotation |
| TURN Server | coturn (for netplay NAT traversal) |

## Development

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full system design.

```bash
# Backend
cd server && go run ./cmd/server

# Web frontend
cd web && npm install && npm run dev

# Player app
# See player/README.md for build instructions
```

## Acknowledgments

Spela is built with and uses the following open-source projects:

- **[retro-game-console-icons](https://github.com/KyleBing/retro-game-console-icons)** (GPL-3.0) — Console hardware icons
- **[EmulatorJS](https://emulatorjs.org)** (GPL-3.0) — Browser-based emulation frontend
- **[libretro / RetroArch](https://www.libretro.com)** (GPL-3.0) — Emulation API and cores
- **[RetroAchievements](https://retroachievements.org)** — Achievement system for retro games

## License

This project is for personal use. Do not distribute copyrighted ROM files.
