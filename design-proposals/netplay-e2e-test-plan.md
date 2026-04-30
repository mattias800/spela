# Netplay E2E Test Plan — Two-Process Desktop Screenshot Verification

**Status:** Draft (from conversation 2026-03-07)

---

## Goal

Verify that netplay input synchronization works end-to-end: when Player 1 presses a button, Player 2's emulator reflects the change, and vice versa. This is validated by comparing screenshots before and after cross-player input.

---

## Architecture: Two-Process Test

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Process 1       │     │   Server     │     │  Process 2       │
│  (Host, Port 0)  │◄───►│  WebSocket   │◄───►│  (Client, Port 1)│
│                  │     │  Relay       │     │                  │
│  NES Core + ROM  │     │  (Docker)    │     │  NES Core + ROM  │
│  Headless emu    │     └──────────────┘     │  Headless emu    │
└─────────────────┘                           └─────────────────┘
```

Both player app instances connect to `ws://server/api/netplay/sessions/{id}/ws`. The server's `NetplayHub` creates a "room" per session and relays all binary frames between clients. No P2P.

---

## Why Two Processes?

The native C bridge (`player/native/src/`) uses global state (`g_core`, `g_video_state`, `g_input_state`). Two emulator instances **cannot run in one JVM process**. The test must use two separate JVM processes communicating with a shared server.

---

## Components to Build

### 1. `NetplayTestRunner.kt` — Headless emulator process

A standalone Kotlin program that acts as a headless netplay player.

**CLI arguments:**
- `--server-url` — E2E backend URL
- `--username` / `--password` — credentials for login
- `--session-id` — netplay session to join
- `--is-host` — whether this instance is the host (port 0) or client (port 1)
- `--local-port` — player port number
- `--core-path` — path to NES libretro core `.dylib`
- `--rom-path` — path to the test ROM

**Behavior:**
- Logs in via HTTP, connects to netplay WebSocket
- Loads core + ROM via JNI, enters netplay lockstep loop
- Reads **stdin commands**: `screenshot <path>`, `press <button>`, `wait <ms>`, `quit`
- Writes `READY` to stdout when emulation is running

### 2. `run-netplay-e2e.sh` — Orchestration script

Shell script that coordinates the full test.

**Steps:**
1. Ensure docker E2E environment is up (`docker-compose.e2e.yml`)
2. Create netplay session + invite code via REST API
3. Build native library if needed
4. Launch two `NetplayTestRunner` processes (host + client)
5. Wait for both to report `READY`
6. Execute the test sequence (see below)
7. Assert screenshot differences
8. Clean up processes and report pass/fail

### 3. Test ROM

**Chip 'n Dale: Rescue Rangers** (NES) — already in `testdata/roms/nes/`.

- Title screen is static with a "1 PLAYER" / "2 PLAYERS" menu
- Pressing Select moves the cursor between options
- Pressing Start begins the game
- In-game, pressing A makes the character jump — a clear visual change for screenshot diffing

---

## Test Sequence

**ROM:** Chip 'n Dale: Rescue Rangers (NES) — `testdata/roms/nes/`

The title screen has a static menu with a cursor. Pressing Select moves the cursor between "1 PLAYER" and "2 PLAYERS". Pressing Start begins the game. In-game, pressing A makes the character jump.

```
Phase 1: Title screen — verify cross-player input (P1 -> P2)

1. Both processes load the ROM and wait for the title screen to render (~3s)
2. P2 takes screenshot -> title_before.png
3. P1 presses Select (moves cursor to "2 PLAYERS")
4. P2 takes screenshot -> title_after.png
5. Assert title_before != title_after
   (proves P1's Select input propagated through the server to P2's emulator)

Phase 2: In-game — verify cross-player input (P2 -> P1)

6. P1 presses Start (starts the game in 2-player mode)
7. Wait ~1s for gameplay to begin
8. P1 takes screenshot -> game_before.png
9. P2 presses A (jump)
10. Wait 10 frames for the jump animation to render
11. P1 takes screenshot -> game_after.png
12. Assert game_before != game_after
    (proves P2's A input propagated through the server to P1's emulator)

Cleanup:

13. Both processes quit, script reports results
```

---

## How Netplay Works (Background)

**Transport**: Server-relayed WebSocket. The server's `NetplayHub` creates a "room" per session and relays all binary frames.

**Emulation loop** (`DesktopLibretroController`):
- `runEmulationLoop()` — normal single-player
- `runNetplayEmulationLoop()` — lockstep netplay

**Netplay loop per frame:**
1. Capture local input, buffer for frame `F + inputDelay`
2. Send to remote via WebSocket (server relay)
3. **Block** until both players' inputs arrive for frame F (5s timeout)
4. Apply all player inputs to JNI input table
5. Run one `nativeRun()` — core is deterministic, same inputs = same output

**State sync**: Host serializes state and sends it in 16KB chunks via `STATE_CHUNK` messages. Client receives and unserializes before emulation starts.

**Key insight**: The libretro core itself doesn't know about netplay — it just receives inputs for port 0 and port 1 each frame and runs deterministically. All synchronization is in Kotlin.

---

## Prerequisites

- Docker E2E environment with netplay WebSocket relay
- NES libretro core binary (nestopia `.dylib`) — either pre-built or downloaded from buildbot
- A suitable test ROM in `testdata/roms/nes/`
- Native library built for desktop

---

## Open Questions

- Should `NetplayTestRunner` be a new Gradle module or a main function in the existing desktop module?
- Screenshot comparison: simple pixel diff (any change = pass) or perceptual hash?
- CI integration: how to ensure core binary availability in CI?
