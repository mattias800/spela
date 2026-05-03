<p align="center">
  <img src="docs/spela-logo.png" alt="Spela" width="300" />
</p>
<p align="center"><em>Nu spelar vi!</em> — Your retro game library, self-hosted. Play anywhere.</p>

<p align="center">
  <a href="#supported-consoles">59 Consoles</a> &bull;
  <a href="#features">Features</a> &bull;
  <a href="#quick-start">Quick Start</a> &bull;
  <a href="#downloads">Downloads</a> &bull;
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

### Player App (Android)

<p align="center">
  <img src="docs/screenshots/android-library.png" alt="Android console browser" width="355" />
  <img src="docs/screenshots/android-games.png" alt="Android SNES game grid" width="355" />
</p>
<p align="center">
  <img src="docs/screenshots/android-game-detail.png" alt="Android game detail" width="355" />
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
| **Metadata Scraping** | Cover art, descriptions, screenshots, ratings, and developer info from IGDB plus libretro thumbnails |
| **Multi-User** | Multiple accounts with owner, admin, and user roles. The first registered user becomes the server owner; subsequent registrations are pending until an admin approves them |
| **RetroAchievements** | Link your RetroAchievements account for in-game achievements with progress tracking |
| **Multi-Server** | The player app can connect to multiple Spela servers |

## Supported Consoles

59 playable systems. Each console shows whether it supports **save states** and **browser play** (via EmulatorJS, no install needed). The seed list also includes scaffolding for newer-gen consoles (Switch, PS5, Wii U, etc.) that aren't yet playable — they show up greyed out in the admin browser.

### Nintendo

| Console | Core | Save States | Browser Play | Extensions |
|---------|------|:-----------:|:------------:|------------|
| NES | Nestopia | :white_check_mark: | :white_check_mark: | `.nes` |
| Famicom Disk System | Nestopia | :white_check_mark: | :white_check_mark: | `.fds` |
| SNES | Snes9x | :white_check_mark: | :white_check_mark: | `.sfc` `.smc` |
| Game Boy | Gambatte | :white_check_mark: | :white_check_mark: | `.gb` |
| Game Boy Color | Gambatte | :white_check_mark: | :white_check_mark: | `.gbc` |
| Game Boy Advance | mGBA | :white_check_mark: | :white_check_mark: | `.gba` |
| Nintendo 64 | Mupen64Plus | :white_check_mark: | :white_check_mark: | `.n64` `.z64` `.v64` |
| Nintendo DS | DeSmuME | :white_check_mark: | :white_check_mark: | `.nds` |
| Virtual Boy | Beetle VB | :white_check_mark: | :white_check_mark: | `.vb` `.vboy` |
| Nintendo GameCube | Dolphin | :white_check_mark: | | `.iso` `.gcm` `.gcz` `.ciso` `.rvz` |
| Nintendo 3DS | Azahar | :white_check_mark: | | `.3ds` `.cci` `.cia` |
| Pokemon Mini | PokeMini | :white_check_mark: | | `.min` |
| Game & Watch | gw | :white_check_mark: | | `.mgw` |

### Sega

| Console | Core | Save States | Browser Play | Extensions |
|---------|------|:-----------:|:------------:|------------|
| Sega SG-1000 | Genesis Plus GX | :white_check_mark: | :white_check_mark: | `.sg` `.sc` `.sf7` `.bin` |
| Sega Master System | Genesis Plus GX | :white_check_mark: | :white_check_mark: | `.sms` |
| Sega Genesis / Mega Drive | Genesis Plus GX | :white_check_mark: | :white_check_mark: | `.md` `.gen` `.bin` |
| Game Gear | Genesis Plus GX | :white_check_mark: | :white_check_mark: | `.gg` |
| Sega CD | ClownMDEmu | :white_check_mark: | :white_check_mark: | `.iso` `.bin` `.cue` `.m3u` |
| Sega 32X | PicoDrive | :white_check_mark: | :white_check_mark: | `.32x` |
| Sega Saturn | Yabause | :white_check_mark: | :white_check_mark: | `.iso` `.bin` `.cue` `.chd` `.m3u` |
| Dreamcast | Flycast | :white_check_mark: | | `.gdi` `.cdi` `.chd` `.cue` `.bin` `.m3u` |

### Sony

| Console | Core | Save States | Browser Play | Extensions |
|---------|------|:-----------:|:------------:|------------|
| PlayStation | Beetle PSX HW | :white_check_mark: | :white_check_mark: | `.bin` `.cue` `.iso` `.pbp` `.m3u` |
| PlayStation 2 | Play! | :white_check_mark: | | `.iso` `.bin` `.cue` `.chd` `.m3u` |
| PSP | PPSSPP | :white_check_mark: | :white_check_mark: | `.iso` `.cso` `.chd` |

### Atari

| Console | Core | Save States | Browser Play | Extensions |
|---------|------|:-----------:|:------------:|------------|
| Atari 2600 | Stella | :white_check_mark: | :white_check_mark: | `.a26` `.bin` |
| Atari 5200 | Atari800 | :white_check_mark: | | `.a52` `.bin` |
| Atari 7800 | ProSystem | :white_check_mark: | :white_check_mark: | `.a78` `.bin` |
| Atari Lynx | Handy | :white_check_mark: | :white_check_mark: | `.lnx` `.lyx` |
| Atari Jaguar | Virtual Jaguar | | :white_check_mark: | `.j64` `.jag` |
| Atari 8-bit (400/800/XL/XE) | Atari800 | :white_check_mark: | | `.atr` `.atx` `.xex` `.car` `.com` `.bin` `.rom` |
| Atari ST | Hatari | :white_check_mark: | | `.st` `.stx` `.msa` `.dim` `.m3u` |

### NEC

| Console | Core | Save States | Browser Play | Extensions |
|---------|------|:-----------:|:------------:|------------|
| TurboGrafx-16 / PC Engine | Beetle PCE | :white_check_mark: | :white_check_mark: | `.pce` |
| TurboGrafx-CD / PC Engine CD | Beetle PCE | :white_check_mark: | :white_check_mark: | `.chd` `.cue` `.iso` `.m3u` |
| PC Engine SuperGrafx | Beetle SuperGrafx | :white_check_mark: | | `.sgx` `.pce` |
| PC-FX | Beetle PC-FX | :white_check_mark: | :white_check_mark: | `.iso` `.cue` `.m3u` |

### SNK

| Console | Core | Save States | Browser Play | Extensions |
|---------|------|:-----------:|:------------:|------------|
| Neo Geo | FBNeo | :white_check_mark: | :white_check_mark: | `.zip` |
| Neo Geo CD | NeoCD | :white_check_mark: | | `.chd` `.cue` `.iso` |
| Neo Geo Pocket / Color | Beetle NGP | :white_check_mark: | :white_check_mark: | `.ngp` `.ngc` |

### Other

| Console | Core | Save States | Browser Play | Extensions |
|---------|------|:-----------:|:------------:|------------|
| 3DO | Opera | :white_check_mark: | :white_check_mark: | `.iso` `.bin` `.cue` `.chd` |
| Philips CD-i | SAME CDi | :white_check_mark: | :white_check_mark: | `.chd` `.cue` `.iso` |
| Arcade (MAME) | MAME 2003+ | :white_check_mark: | :white_check_mark: | `.zip` |
| ScummVM | ScummVM | :white_check_mark: | :white_check_mark: | `.scummvm` (game folders) |
| WonderSwan | Beetle WonderSwan | :white_check_mark: | :white_check_mark: | `.ws` `.wsc` |
| ColecoVision | Gearcoleco | :white_check_mark: | :white_check_mark: | `.col` `.rom` |
| Mattel Intellivision | FreeIntv | :white_check_mark: | | `.int` `.bin` `.rom` |
| Magnavox Odyssey 2 | O2EM | :white_check_mark: | | `.bin` `.o2` |
| Fairchild Channel F | FreeChaF | :white_check_mark: | | `.bin` `.chf` |
| GCE Vectrex | Vecx | :white_check_mark: | | `.bin` `.vec` |
| MSX / MSX2 | BlueMSX | :white_check_mark: | | `.rom` `.dsk` `.cas` `.mx1` `.mx2` |
| Commodore 64 | VICE | :white_check_mark: | :white_check_mark: | `.d64` `.t64` `.prg` `.crt` `.tap` |
| Commodore 128 | VICE | :white_check_mark: | :white_check_mark: | `.d64` `.d71` `.d81` `.t64` `.prg` `.crt` |
| Commodore VIC-20 | VICE | :white_check_mark: | :white_check_mark: | `.prg` `.d64` `.t64` `.crt` |
| Commodore Plus/4 | VICE | :white_check_mark: | :white_check_mark: | `.prg` `.d64` `.t64` |
| Commodore PET | VICE | :white_check_mark: | :white_check_mark: | `.prg` `.d64` `.t64` |
| Commodore Amiga | PUAE | :white_check_mark: | :white_check_mark: | `.adf` `.hdf` `.lha` `.dms` `.zip` |
| DOS | DOSBox Pure | :white_check_mark: | :white_check_mark: | `.exe` `.com` `.bat` `.conf` |

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

### 2. Generate the .env file

```bash
./setup.sh > .env
```

The interactive wizard prompts for hostname, ROM path, optional BIOS path, and optional IGDB credentials. JWT and TURN secrets are generated automatically. (For a non-interactive flow, `./generate-secrets.sh > .env` produces an .env with random secrets and placeholder paths you can edit by hand.)

### 3. Start Spela

```bash
docker compose -f docker-compose.qa.yml up -d --build
```

### 4. Open the web UI

Go to `http://localhost:8080`, register your first account (it becomes the **server owner**), and trigger a library scan from the admin panel. Subsequent registrations are pending admin approval — register a couple of friends and approve them from the admin user list.

### 5. Download the player app

See **[Downloads](#downloads)** below.

---

For production deployment with HTTPS, TURN server, and Portainer, see the **[Deploy Guide](docs/DEPLOY.md)**.

## Downloads

Spela's native player app runs on Android, Windows, macOS, and Linux. Each release publishes pre-built artifacts on the [GitHub Releases page](https://github.com/mattias800/spela/releases/latest):

| Platform | Artifact |
|----------|----------|
| Android | APK |
| macOS | DMG (universal: Intel + Apple Silicon) |
| Windows | MSI installer |
| Linux | AppImage (x86_64), Flatpak, ARM64 binary |

If you don't want to install anything, the web UI's **browser play** mode works on every device that supports WebAssembly + SharedArrayBuffer — no native app needed.

## Documentation

| Guide | Description |
|-------|-------------|
| [Deploy Guide](docs/DEPLOY.md) | Production deployment, HTTPS, TURN server, backups |
| [Development Guide](docs/DEVELOPMENT.md) | Building from source, architecture, testing |
| [Contributing](CONTRIBUTING.md) | Code style, commit conventions, pull requests |

## Acknowledgments

Spela is built with and uses the following open-source projects:

- **[retro-game-console-icons](https://github.com/KyleBing/retro-game-console-icons)** (GPL-3.0) — Console hardware icons
- **[console-logos](https://github.com/PRO100BYTE/console-logos)** — Console logo SVGs by Dan Patrick
- **[Console-Iconset](https://github.com/Tatohead/Console-Iconset)** (Free to use) — Pixel art console and controller icons by Tatohead
- **[Controllers Stencil Platform Images](https://forums.launchbox-app.com/files/file/3480-controllers-stencil-platform-images/)** — White stencil controller icons by EthanAllen
- **[Icons8](https://icons8.com)** — GameCube and 3DS console icons
- **[EmulatorJS](https://emulatorjs.org)** (GPL-3.0) — Browser-based emulation frontend
- **[libretro / RetroArch](https://www.libretro.com)** (GPL-3.0) — Emulation API and cores
- **[libretro-thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails)** (CC0/various) — Box art and screenshot collection
- **[IGDB](https://www.igdb.com/)** — Game metadata (titles, descriptions, ratings, release dates) via the IGDB API
- **[RetroAchievements](https://retroachievements.org)** — Achievement system for retro games
- **[ScummVM project](https://www.scummvm.org)** (GPL-2.0) — ScummVM "Modern Remastered" logo and mascot icon used for the SCUMMVM platform. Logo by the ScummVM Team; original "Modern" mark by Jean Marc Gimenez for the ScummVM project.

## License

This project is for personal use. Do not distribute copyrighted ROM files.
