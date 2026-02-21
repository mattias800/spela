# Fixes Applied - Desktop Player Issues

## Issue 1: Escape Key Not Working on macOS

### Problem
Users couldn't exit games on desktop because the Escape key was not triggering the in-game overlay.

### Root Cause
The `onKeyEvent` modifier requires the component to have focus AND processes events after other handlers. On macOS, the event was being consumed before reaching the Canvas.

### Solution
Changed from `onKeyEvent` to `onPreviewKeyEvent` in `DesktopEmulationSurface.kt`.

**File changed:** `player/shared/src/desktopMain/kotlin/com/spela/player/libretro/DesktopEmulationSurface.kt`

```diff
- import androidx.compose.ui.input.key.onKeyEvent
+ import androidx.compose.ui.input.key.onPreviewKeyEvent

- .onKeyEvent { event ->
+ .onPreviewKeyEvent { event ->
      if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
          onEscapePressed?.invoke()
-         return@onKeyEvent true
+         return@onPreviewKeyEvent true
      }
      // ... rest of handler
  }
```

**Why this works:**
- `onPreviewKeyEvent` fires before focus is considered
- It captures events before they can be consumed by other UI elements
- Escape key now reliably opens the in-game overlay

### Testing
1. Start a game on macOS
2. Press Escape
3. In-game overlay should appear with "Exit Game", "Save", "Load", etc.
4. Press Escape again to close overlay
5. Click "Exit Game" to return to game list

---

## Issue 2: FPS Counter Not Obviously Clickable

### Problem
The FPS counter in the top-right is clickable to open the overlay, but users don't know this because it just looks like a stat display.

### Solution
Added a Menu icon next to the FPS counter to make it obvious it's interactive.

**File changed:** `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/screen/InGameOverlay.kt`

```diff
+ import androidx.compose.material.icons.filled.Menu

- // FPS pill - top right
- Box(
+ // FPS pill with menu icon - top right
+ Row(
      modifier = Modifier
          .align(Alignment.TopEnd)
          .clip(RoundedCornerShape(8.dp))
          .background(SpColor.Scrim)
          .clickable { viewModel.onIntent(EmulationIntent.ToggleOverlay) }
          // ...
          .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall),
+     horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
+     verticalAlignment = Alignment.CenterVertically,
  ) {
+     Icon(
+         imageVector = Icons.Filled.Menu,
+         contentDescription = null,
+         tint = SpColor.OnBackground,
+         modifier = Modifier.size(16.dp),
+     )
      Text(
          text = "%.0f FPS".format(state.fps),
          // ...
      )
- }
+ }
```

**Result:**
- FPS counter now shows as: `☰ 60 FPS`
- The menu icon (☰) makes it obvious it's clickable
- Still shows performance stats while being clearly interactive

---

## Issue 3: Database Initialization Crash (Already Fixed)

### Problem
The desktop app crashed on second launch with "table ServerConnectionEntity already exists".

### Solution
Added try/catch around schema creation in `PlatformModule.kt` (temporary fix).

**File changed:** `player/shared/src/desktopMain/kotlin/com/spela/player/di/PlatformModule.kt`

```kotlin
single {
    val driver = JdbcSqliteDriver("jdbc:sqlite:spela.db")
    try {
        SpelaDatabase.Schema.create(driver)
    } catch (e: Exception) {
        // Schema already exists, which is fine - just continue
    }
    driver
}
```

**Long-term plan:** See `player/DATABASE_MIGRATIONS.md` for proper migration strategy before public release.

---

## How to Test All Fixes

### 1. Build and run desktop app
```bash
./dev-macos.sh
```

### 2. Test Escape key
1. Connect to a server and browse games
2. Start any game (e.g., Super Mario World)
3. **Press Escape** → Overlay should appear
4. **Press Escape again** → Overlay should close
5. **Press Escape** → Click "Exit Game" → Should return to game list

### 3. Test Menu icon click
1. Start a game
2. Look at top-right corner → Should see `☰ 60 FPS` (menu icon + FPS)
3. **Click anywhere on that pill** → Overlay should open
4. Verify all overlay buttons work (Save, Load, Screenshot, Fast Forward, Controls, Exit Game)

### 4. Test database persistence
1. Close the app (Command+Q)
2. Restart with `./dev-macos.sh`
3. App should start normally (no crash)
4. Server connection should be remembered

---

## Known Issues Still To Fix

See `DESKTOP_ISSUES.md` for:
1. **SNES stuttering** — Video rendering tied to Compose frame rate
2. **"Press Esc to pause" hint disappears too quickly** — Only shows for 3 seconds

---

## Commit Message

```
fix(desktop): resolve Escape key and make menu more discoverable

- Switch from onKeyEvent to onPreviewKeyEvent for reliable Escape handling
- Add Menu icon to FPS counter to indicate it's clickable
- Both changes improve desktop UX for exiting games

Fixes #1 (Escape key not working on macOS)
Fixes #2 (Users not knowing how to exit games)
```
