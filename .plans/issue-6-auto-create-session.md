# Plan: Auto-Create Game Session on Play (Issue 6)

## Problem

When the user plays a game via the Play button, no session is created. Auto-save silently discards the save data because `currentSessionId` is null. The user expects to find their progress on the game detail screen, but sees "No sessions yet."

## How the Web UI Does It

The web UI auto-creates sessions in `play-page.tsx`:

1. Play button navigates to `/games/{id}/play/new` (if no prior sessions) or `/games/{id}/play/{sessionId}` (if sessions exist)
2. Play page detects `sessionId === "new"` and calls `POST /api/games/{gameId}/sessions { name: "Default" }`
3. Server creates a `GameSession` record and returns it
4. Emulator uses the returned session ID for saves/play time

**Key design:** Session creation is deferred until actual play, not on page load. Name is always `"Default"`.

## Design for Player App

Replicate the web UI pattern:

1. When `startGame()` is called without a `sessionId`, auto-create one
2. Use name `"Default"` (matching web UI)
3. Set `saveManager.currentSessionId` to the new session ID
4. If session creation fails (e.g. offline), continue playing without a session (graceful degradation)

## Implementation Steps

### Step 1: Add session auto-creation to EmulationViewModel

**File:** `player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt`

In `startGame()`, after BIOS check and before `prepareGameUseCase()`, add:

```kotlin
// Auto-create session if none provided (matches web UI behavior)
var resolvedSessionId = sessionId
if (resolvedSessionId == null && sharedSessionId == null && challengeId == null) {
    try {
        val session = sessionRepository.createSession(gameId, "Default")
        session.onSuccess { resolvedSessionId = it.id }
    } catch (_: Exception) {
        // Offline or server error — continue without session
        println("[Emulation] Failed to auto-create session, continuing without one")
    }
}
saveManager.currentSessionId = resolvedSessionId
```

The guard `sharedSessionId == null && challengeId == null` ensures we don't auto-create sessions for shared sessions or challenge attempts (which have their own lifecycle).

### Step 2: Verify SessionRepository.createSession exists

**File:** `player/shared/src/commonMain/kotlin/com/spela/player/domain/repository/SessionRepository.kt`

Check if `createSession(gameId, name)` exists. If not, add it — the API call is `POST /api/games/{gameId}/sessions { name: "..." }`.

### Step 3: Update state with session ID

After auto-creation, update the emulation state so the UI shows the session:

```kotlin
_state.update { it.copy(sessionId = resolvedSessionId) }
```

### Step 4: Tests

**Desktop unit test** in `EmulationViewModelGameLifecycleTest`:

```kotlin
@Test
fun startGameAutoCreatesSessionWhenNoneProvided() = runTest {
    // Start game without sessionId
    vm.onIntent(EmulationIntent.StartGame(gameId = "1"))
    advanceUntilIdle()

    // Session should have been auto-created
    assertNotNull(vm.state.value.sessionId)
    assertEquals("Default", fakeSessionRepo.lastCreatedName)
}

@Test
fun startGameDoesNotAutoCreateSessionForSharedSession() = runTest {
    vm.onIntent(EmulationIntent.StartGame(gameId = "1", sharedSessionId = "shared1"))
    advanceUntilIdle()

    // Should NOT auto-create a separate session
    assertNull(fakeSessionRepo.lastCreatedName)
}
```

### Step 5: Reuse existing session on subsequent plays

Currently, `onPlay` always sends `PrepareLaunch(gameId)` without a session ID. To reuse an existing session:

**File:** `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/SpelaApp.kt`

The `onPlay` callback could check for existing sessions and pass the most recent one. But this adds complexity to the UI layer.

**Simpler approach:** In `startGame()`, before auto-creating, check if the game already has sessions:

```kotlin
if (resolvedSessionId == null && ...) {
    try {
        val sessions = sessionRepository.getSessionsForGame(gameId)
        if (sessions.isNotEmpty()) {
            resolvedSessionId = sessions.first().id // Use most recent
        } else {
            val session = sessionRepository.createSession(gameId, "Default")
            session.onSuccess { resolvedSessionId = it.id }
        }
    } catch (_: Exception) { ... }
}
```

This matches the web UI behavior: use existing session if available, create "Default" if not.

## Files to Modify

| File | Change |
|------|--------|
| `player/.../viewmodel/EmulationViewModel.kt` | Auto-create/reuse session in `startGame()` |
| `player/.../domain/repository/SessionRepository.kt` | Verify `createSession()` exists |
| `player/.../data/repository/SessionRepositoryImpl.kt` | Implement if missing |
| `player/.../viewmodel/EmulationViewModelGameLifecycleTest.kt` | Tests for auto-creation |

## Edge Cases

- **Offline play:** Session creation fails silently, game still plays, auto-save is discarded (same as current behavior, just explicit)
- **Challenge mode:** Don't auto-create (challenges have their own tracking)
- **Shared sessions:** Don't auto-create (they already have a session)
- **Netplay:** Don't auto-create (netplay has its own lifecycle)
- **Multiple quick plays:** Reuse existing session, don't create duplicates
