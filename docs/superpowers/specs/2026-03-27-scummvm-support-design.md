# ScummVM Support

**Date:** 2026-03-27
**Status:** Approved

## Overview

Add ScummVM as a playable platform in Spela. ScummVM games are directory-based (not single ROM files), but the app's existing multi-file game support (used for multi-disc games) handles most of this already. The main work is adding the console/core seed data, extending the scanner to detect `.scummvm` files, and wiring up the correct input defaults.

## Console & Core Definition

**New console seed:**
- Name: `ScummVM`
- Abbreviation: `SCUMMVM`
- FolderName: `scummvm`
- DefaultCore: `scummvm`
- Extensions: `.scummvm`
- SaveStateSupport: `false` (ScummVM has its own in-game save system)
- Generation: 100 (grouped with home computers)
- Playable: `true`

**New core seed:**
- Name: `scummvm`
- DisplayName: `ScummVM`
- Platforms: `windows,linux,macos,android`
- DownloadURL: empty (uses standard buildbot URL)

No model changes — just new seed data.

## Scanner

The scanner recursively searches inside `scummvm/` for `.scummvm` files. When found:

1. The directory containing the `.scummvm` file is the game
2. Game title derived from the directory name (scraper fixes it later)
3. `Game.FilePath` = relative path to the game directory (e.g., `scummvm/Beneath a Steel Sky/sky`)
4. `Game.FileName` = the `.scummvm` filename (e.g., `sky.scummvm`)
5. All other files in the directory are game data (not scanned individually)

**Nesting support:** The scanner searches recursively, so both flat and nested layouts work:
```
scummvm/monkey1/monkey1.scummvm              ← flat
scummvm/Beneath a Steel Sky/sky/sky.scummvm  ← nested (game is sky/)
```

**`.scummvm` file format:** A single line containing the ScummVM game ID (e.g., `monkey`, `tentacle`, `sky`). This is the standard format expected by the libretro ScummVM core.

**Upload flow:** Admin uploads a zip/tar containing the game directory with a `.scummvm` file inside. Same handling as multi-disc uploads — extract to `scummvm/{dirname}/`.

## Game Serving & Player Download

**Server:** Serves the ScummVM game directory as a tar archive, using the same mechanism already used for multi-disc `.cue`/`.gdi` games.

**Player:** Downloads and extracts the tar archive into `games/{gameId}/`. When launching, passes the path to the `.scummvm` file to `retro_load_game()`, telling the core the game ID and data file location.

No new code needed in the download pipeline — just ensuring ScummVM directory paths flow through the existing tar-serving logic.

## Player Core Loading & Input

**Core loading:** Standard flow, no special handling:
1. Recommended core → `scummvm`
2. Download from buildbot if not cached
3. Download game directory if not cached
4. Load core, load game (`.scummvm` file path)

**Default trackpad:** When no control tab preference is saved for the `scummvm` console, default to `ControlTab.TRACKPAD` instead of `ControlTab.GAMEPAD`. One-line change in the control tab fallback logic.

**Mouse device type:** When launching a ScummVM game, set port 0 to `RETRO_DEVICE_MOUSE` so the core receives mouse input from the trackpad tab. Added in `EmulationViewModel.startGame()` alongside the existing dual-screen detection logic.

## Testing

**Server (Go unit tests):**
- Scanner detects `.scummvm` files recursively in `scummvm/` directory
- Scanner creates Game record with correct directory path and filename
- Game download serves ScummVM game directory as tar archive

**Player (Desktop E2E):**
- ScummVM console defaults control tab to Trackpad
- ScummVM console has `saveStateSupport = false`

## Out of Scope

- Main screen trackpad mode (relative cursor on primary display for single-screen devices)
- Auto-detection of ScummVM games by folder name (without `.scummvm` file)
- Web admin UI helper for creating `.scummvm` files
- ScummVM-specific scraper integration
- ScummVM core variable configuration (e.g., graphics scaler, audio settings)
