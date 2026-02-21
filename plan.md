# Code Review: Netplay Implementation

**Reviewer:** code-reviewer
**Date:** 2026-02-15
**Task:** #10 - Code review all netplay implementations

## CRITICAL Issues

### C1. Backend/Web Type Mismatch -- `hostUserId` vs `hostId`, `clientUserId` vs `clientId`
**Files:** `server/internal/api/responses.go:473-474,477`, `web/src/types/api.ts:505-508`

The backend `NetplaySessionResponse` returns `hostUserId` and `clientUserId` (JSON: `"hostUserId"`, `"clientUserId"`). The web TypeScript type expects `hostId` and `clientId`. This means:
- `session.hostId` is always `undefined` on the frontend
- `session.clientId` is always `undefined` on the frontend
- The "is host" check (`session.hostId === user?.id`) in `netplay-session-page.tsx:62` never matches, so the host cannot see the Cancel button
- The player count in `netplay-player-list.tsx:19` (`session.clientId ? 2 : 1`) always shows 1
- Player 2 slot in `netplay-player-list.tsx:31` is never rendered

**Fix:** Either rename the backend JSON tags to `hostId`/`clientId` for consistency, or update the web types to match the backend's `hostUserId`/`clientUserId`.

### C2. Backend/Web Type Mismatch -- `name` field does not exist
**Files:** `web/src/types/api.ts:504`, `web/src/pages/netplay-session-page.tsx:124,260`

The web `NetplaySession` type has a `name` field, but the backend `NetplaySessionResponse` has no `name` field. The `netplay-session-page.tsx` renders `session.name` as the page title (line 124) and in the cancel modal (line 260). This will render `undefined` in the UI.

The backend also has no `name` field in the `CreateSession` request, yet `useCreateNetplaySession` sends a `name` field (`use-netplay.ts:35`).

**Fix:** Either add a `name` field to the server's `NetplaySession` model and response, or remove `name` from the web type and display `session.gameTitle` instead.

### C3. Backend/Web/KMP API Mismatch -- ListSessions returns array, clients expect paginated wrapper
**Files:** `server/internal/api/netplay_handler.go:106-111`, `web/src/hooks/use-netplay.ts:8-18`, `web/src/types/api.ts:524-529`, `player/shared/.../NetplayRepositoryImpl.kt:24-25`

The backend `ListSessions` handler returns a raw `[]NetplaySessionResponse` JSON array (line 111). But:
- The web hook sends `page` and `pageSize` query params that the handler ignores
- The web expects `NetplaySessionsResponse` with `{ data, total, page, pageSize }` wrapper
- The KMP `NetplayRepositoryImpl` calls `.data` on the response, expecting a wrapper

This means the `sessionsData?.data` access in `netplay-page.tsx:50` will be `undefined` (the array doesn't have a `.data` property), so sessions will never render. Similarly, the KMP client will fail to parse the response.

**Fix:** Either wrap the backend response in a `PaginatedResponse` (consistent with other list endpoints), or change the clients to expect a raw array.

### C4. Supported consoles lists are inconsistent across all three components
**Files:** `server/internal/api/netplay_handler.go:19-21`, `web/src/components/netplay/netplay-consoles.ts:1-8`, `player/shared/.../NetplayModels.kt:30`

| Console    | Server (abbreviation)  | Web (consoleName)     | KMP (lowercase)      |
|------------|------------------------|-----------------------|----------------------|
| NES        | NES                    | NES                   | nes                  |
| SNES       | SNES                   | SNES                  | snes                 |
| GB         | GB                     | Game Boy              | gb                   |
| GBC        | --(not listed)         | --(not listed)        | gbc                  |
| GBA        | GBA                    | Game Boy Advance      | gba                  |
| GEN        | GEN                    | Genesis               | genesis              |
| Mega Drive | --(not listed)         | Mega Drive            | megadrive            |

Problems:
- **Server uses abbreviations** (NES, SNES, GB, GBA, GEN) while **web uses full console names** (NES, SNES, Game Boy, Game Boy Advance, Genesis, Mega Drive). The web filter compares against full names but this happens to work because `consoleName` in `GameResponse` comes from `Console.Name`.
- **GBC** is only in the KMP list, not in the server or web. A player app user could try to start a GBC game via netplay, only for the server to reject it.
- **Mega Drive** is in the web and KMP lists but not in the server list. A user could select a Mega Drive game in the web create modal, but the server would reject it.

**Fix:** Unify the supported consoles list. Add GBC to the server list if intended, and add GEN (for Mega Drive) to the server list. The web and KMP lists must exactly match whatever the server accepts.

## HIGH Issues

### H1. WebSocket binary messages sent as TextMessage on the write pump
**File:** `server/internal/websocket/netplay_hub.go:245`

The `netplayWritePump` sends all messages as `websocket.TextMessage`:
```go
func (c *NetplayClient) netplayWritePump() {
    for msg := range c.Send {
        if err := c.Conn.WriteMessage(websocket.TextMessage, msg); err != nil {
```

However, the `netplayReadPump` distinguishes between `BinaryMessage` and text frames (line 210-214). When a client sends a binary frame (input data), the server broadcasts it via `broadcastExcept` -> client's `Send` channel -> `netplayWritePump`, which re-sends it as `TextMessage`. The receiving client's Ktor WebSocket will interpret this as text, not binary, breaking the binary protocol parsing.

**Fix:** Either (a) add a message type byte prefix to the channel data so the write pump can distinguish text vs binary, or (b) use separate channels for text and binary messages.

### H2. `runBlocking` in emulation loop with polling `delay(1)` wastes CPU
**Files:** `player/shared/src/androidMain/.../AndroidLibretroController.kt:361-368`, `player/shared/src/desktopMain/.../DesktopLibretroController.kt:241-249`

Both `runNetplayEmulationLoop` methods use `runBlocking { inputBuffer.awaitInputsForFrame(...) }`, which internally polls with `delay(1)` in a tight loop. At 60fps, each frame has a 16ms budget, and the polling loop runs up to 5000 iterations (5s timeout) calling `delay(1)` each time. This creates significant CPU overhead from coroutine scheduling inside `runBlocking`.

**Fix:** Replace the polling loop with a notification mechanism (e.g., `CompletableDeferred<Map<Int, InputState>>` per frame, or a `Channel`-based approach where the network thread signals when inputs arrive).

### H3. Invite code uniqueness not retried on collision
**File:** `server/internal/api/netplay_handler.go:68,373-386`

`generateInviteCode()` generates a random 6-char code from a 30-char alphabet (~729 million combinations). The code has a `uniqueIndex` constraint (models.go:392), so the DB will reject duplicates, but the handler doesn't retry on collision -- it returns a generic 500 error to the user.

**Fix:** Add retry logic (2-3 attempts) when `Create` fails due to unique constraint violation on the invite code.

### H4. GetSession exposes any session to any authenticated user
**File:** `server/internal/api/netplay_handler.go:115-122`

`GetSession` returns session details to any authenticated user without access control. While "waiting" sessions should be discoverable (for joining), sessions in "in_progress" or "ended" state probably shouldn't be viewable by uninvolved users.

**Fix:** Either (a) restrict `GetSession` to participants for non-waiting sessions, or (b) document this as intentional for spectator-like visibility.

### H5. No cleanup of stale waiting sessions
**File:** `server/internal/db/models.go:377-395`

There is no TTL or cleanup mechanism for sessions that remain in "waiting" status indefinitely. If a host creates a session and then disconnects, the session lingers forever. The list endpoint will accumulate stale sessions over time.

**Fix:** Add a periodic cleanup goroutine that marks sessions older than N minutes as ended with `endReason: "timeout"`. Or add a `WHERE created_at > ?` filter in `ListSessions`.

### H6. CancellationException suppressed in NetplaySignaling
**File:** `player/shared/.../NetplaySignaling.kt:99`

```kotlin
} catch (_: Exception) {
    session = null
}
```

This catches all exceptions including `CancellationException`, which is special in Kotlin coroutines. Suppressing it prevents proper coroutine cancellation, potentially causing the reconnection loop to continue running after the scope is cancelled.

**Fix:** Re-throw `CancellationException`:
```kotlin
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    session = null
}
```

### H7. Web `NetplaySession` type field `gameConsoleName` mismatches backend `consoleName`
**File:** `web/src/types/api.ts:514`, `server/internal/api/responses.go:483`

The backend `NetplaySessionResponse` has JSON tag `consoleName`, but the web type has `gameConsoleName`. The `gameConsoleName` field will always be `undefined`. This affects:
- `netplay-session-page.tsx:127` - Console badge shows nothing
- `netplay-session-row.tsx:58` - Console badge in session list shows nothing

**Fix:** Rename the web type field to `consoleName` to match the backend, or rename the backend JSON tag to `gameConsoleName`.

## MEDIUM Issues

### M1. Code duplication: lockstep emulation loop in both controllers
**Files:** `AndroidLibretroController.kt:335-419`, `DesktopLibretroController.kt:216-296`

`runNetplayEmulationLoop()`, `captureLocalInput()`, and `applyInputToJni()` are nearly identical between Android and Desktop controllers. The only difference is Android includes `updateVideoFrame()` and `pushAudio()` calls.

**Fix:** Extract common lockstep logic into a shared utility. Platform controllers can provide callbacks for platform-specific work (video/audio).

### M2. `toSessionResponse` has fallback N+1 queries
**File:** `server/internal/api/netplay_handler.go:390-415`

The `toSessionResponse` method does fallback DB queries to load host/client user data if the associations weren't preloaded. This is a code smell -- it masks missing preloads and adds silent N+1 queries.

**Fix:** Remove the fallback queries and ensure all callers use `loadSession` or explicit Preload consistently.

### M3. `WebSocketRelayTransport.sendControl` is a silent no-op
**File:** `player/shared/.../WebSocketRelayTransport.kt:41-45`

The `sendControl` method does nothing. The interface contract implies callers can send control messages, but they silently vanish. Any future caller using this method will be surprised.

**Fix:** At minimum, log a warning. Better: throw `UnsupportedOperationException` or return a result type indicating not supported.

### M4. `handleTextFrame` silently drops unparsable messages
**File:** `player/shared/.../NetplaySignaling.kt:139-167`

Messages with missing required fields are silently dropped via early `return`. No logging occurs. This makes debugging signaling issues very difficult.

**Fix:** Add debug-level logging for dropped messages.

### M5. `NetplayInputBuffer.awaitInputsForFrame` busy-waits with 1ms delay
**File:** `player/shared/.../NetplayInputBuffer.kt:71-87`

The polling loop with `delay(1)` is wasteful. For a 60fps game with 16ms frame budget, this creates up to 16 coroutine suspend/resume cycles per frame.

**Fix:** Use a condition-based notification mechanism instead of polling.

### M6. `deleteSession` in `NetplayViewModel` triggers full reload
**File:** `player/shared/.../NetplayViewModel.kt:74-85`

On successful delete, `deleteSession` calls `loadSessions()` which sets `isLoading = true`. This causes a loading flash. Should optimistically remove the session from the local list instead.

### M7. DesktopLibretroController.stop() doesn't clean up netplay
**File:** `player/shared/src/desktopMain/.../DesktopLibretroController.kt:100-106`

The Android `stop()` method calls `netplayTransport?.disconnect()` and `clearNetplayMode()` (lines 148, 153). The Desktop `stop()` does NOT call `netplayTransport?.disconnect()` or `clearNetplayMode()`, which means the transport stays connected and netplay state is stale after stopping.

**Fix:** Add `netplayTransport?.disconnect()` and `clearNetplayMode()` to Desktop's `stop()` method, matching the Android implementation.

### M8. `EmulationViewModel` has 14 constructor dependencies
**File:** `player/shared/.../EmulationViewModel.kt:41-57`

The constructor takes 14 dependencies. Adding `apiClient` and `engineFactory` for netplay contributes to this. This violates single responsibility.

**Fix:** Extract netplay setup into a `NetplaySessionManager` or use case class that the ViewModel delegates to.

## LOW Issues

### L1. `generateInviteCode` fallback on random error is predictable
**File:** `server/internal/api/netplay_handler.go:380`

If `rand.Int` fails, `code[i] = inviteCodeAlphabet[i]` produces predictable characters. This is extremely unlikely with `crypto/rand` but the fallback undermines the randomness.

**Fix:** Return an error instead of using a predictable fallback.

### L2. `remotePort` computed but never used in Android lockstep loop
**File:** `player/shared/src/androidMain/.../AndroidLibretroController.kt:339`

```kotlin
val remotePort = if (localPort == 0) 1 else 0
```
Dead code -- `remotePort` is never referenced.

**Fix:** Remove the unused variable.

### L3. No unit tests for `NetplayProtocol` binary encode/decode
**Files:** `player/shared/.../NetplayProtocol.kt`

The binary protocol lacks round-trip tests. Given the critical nature of correct binary encoding (endianness, field positions), this is a testing gap.

**Fix:** Add tests verifying round-trip encode/decode for all message types (input frame, state chunk, desync check).

### L4. `setLocalInput` and `setRemoteInput` are identical implementations
**File:** `player/shared/.../NetplayInputBuffer.kt:21-34`

Both methods are byte-for-byte identical. While separate methods improve semantics, they could share a private implementation to reduce surface area for bugs.

### L5. Web test coverage is thin
**File:** `web/src/components/netplay/__tests__/netplay-create-modal.test.tsx`

Only the create modal has a unit test. The session page, session row, player list, and hooks lack unit tests.

### L6. `netplay-consoles.ts` hardcodes list that should come from server
**File:** `web/src/components/netplay/netplay-consoles.ts`

This file duplicates the supported console list from the server. If the server adds/removes console support, the web must be manually updated.

**Fix:** Consider exposing `/api/netplay/supported-consoles` from the server.

### L7. Minimal hub tests -- no WebSocket integration test
**File:** `server/internal/websocket/netplay_hub_test.go`

Tests cover basic room management but don't test WebSocket upgrade, read/write pumps, or message routing through real WebSocket connections.

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 4     |
| HIGH     | 7     |
| MEDIUM   | 8     |
| LOW      | 7     |

**Top priorities for the development team:**
1. **C1-C3**: Fix type/API mismatches between backend and web/KMP -- the web frontend is fundamentally broken and will not render sessions correctly
2. **C4**: Unify supported consoles list -- users will be able to select games the server rejects
3. **H1**: Fix binary-as-text WebSocket bug -- netplay data relay will not function
4. **H6**: Fix CancellationException suppression -- causes resource leaks in KMP
5. **H7**: Fix `gameConsoleName` vs `consoleName` field mismatch -- console badges are empty
6. **M7**: Fix Desktop stop() not cleaning up netplay -- resource leak on desktop
