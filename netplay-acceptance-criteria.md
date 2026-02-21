# Real-Time Netplay: Phase 1 Acceptance Criteria

## Overview

Phase 1 delivers **server-relayed real-time netplay** for two players. One player hosts a session, the other joins via an invite code. Both players play the same game simultaneously with inputs relayed through the server. This is distinct from the existing Relay feature (asynchronous turn-taking); netplay is synchronous, real-time, same-screen multiplayer over the network.

Phase 1 is **server-relay only** -- all input data flows through the Spela server. No peer-to-peer, no TURN/STUN, no UDP. This keeps the architecture simple and self-hosted friendly.

---

## Scope: What Phase 1 Includes

- Two-player netplay only (host + one client)
- Server-relayed input synchronization via WebSocket
- Deterministic lockstep emulation (both players run the same inputs on the same frame)
- Session creation, invite codes, joining, and leaving
- Input delay configuration (host-controlled)
- Web UI for session management (create, view, share invite code)
- Player app UI for creating, joining, and playing netplay sessions
- Graceful handling of disconnection and reconnection

## Scope: What Phase 1 Does NOT Include

- More than 2 players
- Peer-to-peer / UDP transport
- Spectator mode
- Cross-server netplay
- Save state sync during netplay
- Netplay for consoles that are not deterministic (Phase 1 should clearly list supported consoles)
- Voice chat or text chat during gameplay

---

## Acceptance Criteria

### AC-1: Session Creation (Player App)

**Given** a user is on the game detail screen for a supported game,
**When** they tap a "Netplay" button,
**Then** a netplay session is created on the server and the user sees:
- A unique 6-character alphanumeric invite code (uppercase, no ambiguous characters like 0/O, 1/I/L)
- A prominent "Copy Code" button that copies the code to the clipboard
- A "Waiting for player..." status indicator
- The game and console name displayed clearly
- A "Cancel" button to abandon the session before anyone joins

**Testable:** Tapping Netplay creates a session. The invite code is visible. Copy button works. Cancel destroys the session.

### AC-2: Session Creation (Web UI)

**Given** a user is on the game detail page in the web UI,
**When** they click "Create Netplay Session",
**Then** a session is created and the web shows:
- The invite code with a "Copy to Clipboard" button (with visual confirmation like "Copied!" tooltip)
- Session status: "Waiting for player"
- A QR code or deep link that opens the session directly in the player app (nice-to-have, not required for Phase 1)
- A "Cancel Session" button

**Note:** The web UI does NOT allow playing -- it only manages sessions. The actual gameplay happens in the player app.

### AC-3: Joining a Session (Player App)

**Given** a user has a valid invite code,
**When** they navigate to "Join Netplay" and enter the code,
**Then:**
- The app validates the code against the server
- If valid: shows the game name, host username, and a "Join" confirmation button
- If invalid/expired: shows a clear error: "Session not found. The code may have expired or the host may have cancelled."
- If session is full: shows "This session already has two players."
- If the user doesn't have the game's ROM downloaded: shows "You need to download [Game Name] first" with a button to go to the game detail page

**After joining:**
- Both host and client see each other's usernames
- Both see a "Starting game..." loading state
- The game launches automatically within 3 seconds of both players being ready

**Testable:** Valid code joins successfully. Invalid code shows error. Full session shows error. Missing ROM shows download prompt. Game auto-launches after join.

### AC-4: Joining a Session (Web UI)

**Given** a user is on the web netplay page,
**When** they enter an invite code,
**Then** they see session details (game, host, status) but **cannot join as a player**. The web shows a message: "Open this session in the Spela player app to join."

### AC-5: Netplay Lobby Screen (Player App)

**Given** both players have connected to a netplay session,
**Then** both players see a lobby screen showing:
- Game name and cover art
- Both players' usernames and avatars (host marked with a crown/host icon)
- Connection quality indicator (ping to server, shown as a colored dot: green < 50ms, yellow < 150ms, red > 150ms)
- Input delay setting (host can adjust, 1-10 frames, default: 3)
- A brief, non-technical explanation of input delay: "Higher delay = smoother online play. Lower delay = more responsive controls. Start with 3 and adjust if you notice stuttering."
- "Start Game" button (host only)
- "Leave" button (both players)

**Testable:** Both players see the lobby. Host can adjust delay. Host can start. Both can leave.

### AC-6: Gameplay -- Input Synchronization

**Given** a netplay session is in progress,
**Then:**
- Both players' inputs are relayed through the server
- Both emulators run in lockstep -- same inputs applied on the same frame
- Player 1 (host) controls pad 1, Player 2 (client) controls pad 2
- The configured input delay is applied to both players equally
- The game state remains synchronized (no desync for deterministic cores)

**Testable:** Both players see identical game states. Inputs from both players affect the game correctly. Frame-by-frame state matches.

### AC-7: In-Game Netplay HUD

**Given** a netplay session is actively being played,
**Then** both players see a minimal netplay overlay showing:
- The other player's username (small, top corner, semi-transparent)
- Current ping (updated every 2 seconds)
- If ping exceeds 200ms: a subtle warning icon appears (no intrusive popup)
- The overlay does NOT obscure gameplay and can be toggled off in emulation settings

**Testable:** HUD shows peer name and ping. Warning appears at high ping. HUD can be toggled.

### AC-8: Pausing

**Given** a netplay session is in progress,
**When** either player pauses the emulator,
**Then:**
- Both emulators pause simultaneously
- Both players see "Paused by [username]"
- Either player can unpause (and both resume simultaneously)
- If a player is paused for more than 5 minutes, the other player sees: "Waiting for [username] to resume. You can leave the session."

**Testable:** Pause propagates to both players. Pause attribution is shown. Unpause works from either side.

### AC-9: Disconnection -- Client Disconnects

**Given** a netplay session is in progress and the client (player 2) disconnects,
**Then:**
- The host's game pauses immediately
- The host sees: "[Username] disconnected. Waiting for reconnection..." with a countdown timer (60 seconds)
- If the client reconnects within 60 seconds: game resumes from the paused state
- If the client does not reconnect: the host sees "Player disconnected. Session ended." and the game continues in single-player mode (optional) or returns to the lobby

**Testable:** Client disconnect pauses host. Reconnection resumes game. Timeout ends session gracefully.

### AC-10: Disconnection -- Host Disconnects

**Given** a netplay session is in progress and the host (player 1) disconnects,
**Then:**
- The client's game pauses immediately
- The client sees: "Host disconnected. Waiting for reconnection..." with a countdown timer (60 seconds)
- If the host reconnects within 60 seconds: game resumes
- If the host does not reconnect: the client sees "Host disconnected. Session ended." and returns to the home screen

**Testable:** Host disconnect pauses client. Reconnection resumes. Timeout ends session and navigates away.

### AC-11: Leaving a Session Gracefully

**Given** a netplay session is in progress,
**When** a player presses the "Leave Netplay" button (accessible from the pause menu),
**Then:**
- A confirmation dialog appears: "Leave the netplay session? The other player will be disconnected."
- On confirm: the leaving player's game stops, the other player sees "[Username] left the session" and can continue in single-player or exit
- The session is marked as ended on the server

**Testable:** Leave shows confirmation. Confirming ends session for both. Other player gets notification.

### AC-12: Session Expiration

**Given** a session has been created but no one has joined,
**When** 15 minutes pass without a join,
**Then** the session expires automatically and the host sees "Session expired -- no one joined. You can create a new session."

**Testable:** Unjoined sessions expire after 15 minutes. Host gets a clear message.

### AC-13: Error States

The following error scenarios must be handled with clear, actionable messages:

| Error | Message |
|-------|---------|
| Server unreachable during session | "Lost connection to server. Attempting to reconnect..." (with retry) |
| Game ROM mismatch | "Both players must have the same version of [Game Name]. Please re-download the game." |
| Unsupported console | "Netplay is not supported for [Console Name] yet." |
| Session already ended | "This session has already ended." |
| User tries to join their own session | "You can't join your own session." |
| Server at capacity (if applicable) | "The server is busy. Try again in a moment." |

**Testable:** Each error scenario produces the specified message.

### AC-14: Netplay Session List (Web UI)

**Given** a user navigates to the Netplay page in the web UI,
**Then** they see:
- A list of their active and recent netplay sessions
- Each session shows: game name, other player, status (waiting/in-progress/ended), duration, invite code (for waiting sessions)
- A "Create Session" button
- Empty state: "No netplay sessions yet. Create one to play with a friend."

**Testable:** Session list renders. Active and ended sessions appear. Empty state shows when no sessions exist.

### AC-15: Netplay Section on Home Screen (Player App)

**Given** the user has an active netplay session or pending invite,
**Then** the home screen shows a "Netplay" section at the top:
- Active session: "[Game Name] with [Username] -- Tap to rejoin"
- Pending invite: "You have a netplay invite from [Username] -- Tap to view"
- If no active sessions: section is not shown (no empty state needed)

**Testable:** Active session shows rejoin card. Pending invite shows notification. Section hidden when nothing is active.

### AC-16: Supported Consoles

Phase 1 netplay is only available for consoles with deterministic libretro cores. At minimum:
- NES
- SNES
- Game Boy
- Game Boy Advance
- Genesis/Mega Drive

The "Netplay" button must be hidden or disabled for unsupported consoles, with a tooltip: "Netplay is not yet supported for this console."

**Testable:** Netplay button appears for supported consoles. Button is disabled/hidden for unsupported ones.

### AC-17: Input Delay Explanation

**Given** a user has never used netplay before,
**When** they first enter a netplay lobby,
**Then** a one-time tooltip or info card appears:
- "Online multiplayer uses a small input delay to keep both players in sync. You might notice your button presses take a moment to register. This is normal! Adjust the delay setting if the game feels too sluggish or too choppy."
- This message is dismissable and does not appear again after dismissal

**Testable:** First-time tooltip appears. It can be dismissed. It does not reappear.

### AC-18: Performance -- Acceptable Latency

**Given** both players are connected to the same server with < 50ms ping each,
**Then:**
- Total input-to-screen latency with 3 frames of input delay is under 150ms
- The game runs at full speed (60 FPS for most consoles) without frame drops caused by netplay synchronization
- No visible stutter or hitching during normal gameplay

**Testable:** Measure FPS and input latency under controlled network conditions. Full speed is maintained.

### AC-19: Data Model

A netplay session on the server must track:
- Session ID (UUID or similar)
- Invite code (6-char, unique, case-insensitive)
- Game ID (reference to the game being played)
- Host user ID
- Client user ID (null until joined)
- Status: `waiting`, `in_progress`, `ended`
- Input delay (frames)
- Created timestamp
- Started timestamp (when game actually begins)
- Ended timestamp
- End reason: `host_left`, `client_left`, `timeout`, `completed`

**Testable:** All fields exist in the database. Status transitions work correctly. Timestamps are set at the right times.

### AC-20: API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/netplay/sessions | Create a new session |
| GET | /api/netplay/sessions | List user's sessions |
| GET | /api/netplay/sessions/:id | Get session details |
| POST | /api/netplay/sessions/join | Join by invite code |
| POST | /api/netplay/sessions/:id/leave | Leave a session |
| PUT | /api/netplay/sessions/:id/settings | Update input delay (host only) |
| DELETE | /api/netplay/sessions/:id | Cancel a waiting session (host only) |
| WS | /api/netplay/sessions/:id/ws | WebSocket for real-time input relay |

**Testable:** Each endpoint returns correct responses and status codes. Authorization is enforced (host-only actions, member-only access).

---

## Non-Functional Requirements

- **Self-hosted friendly:** No external service dependencies. Everything runs on the Spela server.
- **Bandwidth:** Input data only (not video/audio). WebSocket messages should be < 100 bytes per frame.
- **Security:** Sessions are private. Only invited players can join. Invite codes expire with the session. WebSocket connections require authentication.
- **Cleanup:** Ended and expired sessions are cleaned up (soft-deleted) automatically.

---

## Out of Scope for Phase 1 (Explicitly Deferred)

1. UDP transport / peer-to-peer
2. More than 2 players
3. Spectator mode
4. Rollback netcode (Phase 1 uses lockstep only)
5. Cross-server federation
6. Voice/text chat
7. Save/load during netplay
8. Replay recording
9. Matchmaking (all sessions are private, invite-code-only)

---

## Definition of Done

All acceptance criteria (AC-1 through AC-20) pass. The full test suite passes with no regressions:
- Go unit tests for all netplay endpoints
- Web Playwright E2E tests for the netplay management pages
- Web Vitest unit tests for netplay hooks
- Player Android E2E tests for session creation, joining, lobby, and disconnection handling
- Player Desktop E2E tests for the same scenarios
- No regressions in any existing test suite
