# Debugging Escape Key Not Working on macOS

## Issue
User reports Escape key is not working on macOS desktop build.

## Expected Behavior
1. User starts a game
2. Presses Escape key
3. In-game overlay appears with "Exit Game", "Save", "Load", etc. buttons

## Code Path
The Escape key handler is implemented in several places:

### 1. `DesktopEmulationSurface.kt` (lines 117-122)
```kotlin
.onKeyEvent { event ->
    // Handle Escape key to toggle overlay
    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
        onEscapePressed?.invoke()
        return@onKeyEvent true
    }
    // ...
}
```

### 2. `PlatformEmulationSurface.desktop.kt` (line 61)
```kotlin
DesktopEmulationSurface(
    controller = desktopController,
    selectedShader = selectedShader,
    modifier = modifier,
    onEscapePressed = onEscapePressed,  // <-- callback passed through
    keyMapping = keyMapping,
)
```

### 3. `SpelaApp.kt` (lines 468-473)
```kotlin
PlatformEmulationSurface(
    controller = libretroController,
    selectedShader = emulationState.selectedShader,
    onEscapePressed = {
        emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
    },
)
```

## Potential Issues

### Issue A: Focus not on Canvas
The `onKeyEvent` only triggers if the Canvas has focus. The focus management code:

```kotlin
// DesktopEmulationSurface.kt lines 94-101
LaunchedEffect(Unit) {
    while (true) {
        focusRequester.requestFocus()
        delay(500)
    }
}
```

**Problem:** The focus requester runs every 500ms, but if there's another focusable element (like the overlay), it might steal focus.

**Test:** Add logging to see if key events are being received at all:
```kotlin
.onKeyEvent { event ->
    println("DEBUG: Key event received: ${event.key}, type: ${event.type}")
    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
        println("DEBUG: Escape key pressed!")
        onEscapePressed?.invoke()
        return@onKeyEvent true
    }
    // ...
}
```

### Issue B: macOS intercepts Escape key
macOS might intercept Escape for system functions (like exiting fullscreen).

**Test:** Try a different key (like 'P' for pause) to see if key events work at all:
```kotlin
if ((event.key == Key.Escape || event.key == Key.P) && event.type == KeyEventType.KeyDown) {
    onEscapePressed?.invoke()
    return@onKeyEvent true
}
```

### Issue C: Overlay/InGameOverlay consuming events
The `InGameOverlay` component might be consuming all keyboard events even when invisible.

**Check:** Look at `InGameOverlay.kt` — does it have any `.onKeyEvent` handlers that might be intercepting Escape?

## Quick Fixes

### Fix 1: Add keyboard shortcut to Window level
Instead of relying on focus, handle Escape at the Window level in `Main.kt`:

```kotlin
fun main() = application {
    startKoin {
        modules(commonModule, platformModule())
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Spela",
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
        onKeyEvent = { event ->
            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                // TODO: Need to get emulationViewModel here
                // Option: Use global event bus or shared state
                return@Window true
            }
            false
        }
    ) {
        App()
    }
}
```

Problem: Can't easily access `emulationViewModel` from `Main.kt`.

### Fix 2: Use `onPreviewKeyEvent` instead of `onKeyEvent`
`onPreviewKeyEvent` fires before focus is considered:

```kotlin
// In DesktopEmulationSurface.kt, change:
.onKeyEvent { event ->
// To:
.onPreviewKeyEvent { event ->
```

This might capture the key before other elements can intercept it.

### Fix 3: Add click handler to open overlay
As a workaround until Escape is fixed, make the FPS counter more obvious:

```kotlin
// Make the FPS counter bigger and add a visible "MENU" label
Box(
    modifier = Modifier
        .align(Alignment.TopEnd)
        .clip(RoundedCornerShape(12.dp))
        .background(Color.Black.copy(alpha = 0.5f))
        .clickable { viewModel.onIntent(EmulationIntent.ToggleOverlay) }
        .padding(horizontal = 12.dp, vertical = 8.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Menu, "Menu", tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text("%.0f FPS".format(state.fps), color = Color.White)
    }
}
```

## Debugging Steps

1. **Add logging:** Put print statements in the `onKeyEvent` handler to see if events are received
2. **Test other keys:** Try 'P' or 'M' to see if keyboard input works at all
3. **Check focus:** Add visual indicator showing when Canvas has focus
4. **Test in different states:** Does Escape work before starting a game? After opening/closing overlay once?

## Immediate Workaround for Users

Until fixed, users can:
1. **Click the FPS counter** in the top-right to open the overlay
2. **Use Command+Q** to quit the entire app (not ideal, loses progress)
3. **Alt+Tab** away and force quit (even worse)

## Recommended Fix

Implement Fix #2 (use `onPreviewKeyEvent`) AND Fix #3 (make FPS counter more discoverable).

This addresses both:
- The technical issue (Escape not working)
- The UX issue (users not knowing how to exit)
