# Desktop Player Known Issues & Solutions

## Issue 1: No obvious way to exit games on desktop

### Current State
- **Escape key DOES work** — pressing Escape opens the in-game overlay with "Exit Game" button
- A hint "Press Esc to pause" shows for 3 seconds when a game starts
- The FPS counter in the top-right is also clickable to open the overlay

### Problem
- The 3-second hint disappears too quickly
- Many users won't discover the Escape key shortcut
- The FPS counter click affordance is not obvious

### Solutions

#### Option A: Persistent UI Hint (Recommended)
Add a small, semi-transparent button/icon in the corner that's always visible:

```kotlin
// In DesktopEmulationSurface.kt, add alongside the "Press Esc" hint:
Box(
    modifier = Modifier
        .align(Alignment.TopLeft)
        .padding(8.dp)
) {
    IconButton(
        onClick = { onEscapePressed?.invoke() },
        modifier = Modifier
            .size(32.dp)
            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
    ) {
        Icon(
            Icons.Default.Menu, // or Pause icon
            contentDescription = "Pause game",
            tint = Color.White.copy(alpha = 0.7f)
        )
    }
}
```

#### Option B: Show hint on mouse movement
Keep the hint hidden, but show it when the mouse moves (like video players do):

```kotlin
var showControls by remember { mutableStateOf(false) }
var lastMouseMove by remember { mutableStateOf(System.currentTimeMillis()) }

LaunchedEffect(lastMouseMove) {
    showControls = true
    delay(3000)
    if (System.currentTimeMillis() - lastMouseMove > 2900) {
        showControls = false
    }
}

Box(modifier = Modifier.pointerInput(Unit) {
    detectPointerInput { event ->
        lastMouseMove = System.currentTimeMillis()
    }
})
```

#### Option C: Add menu bar (macOS native)
On macOS, add a native menu bar:

```kotlin
// In Main.kt
Window(
    ...,
    state = rememberWindowState(width = 1280.dp, height = 720.dp),
) {
    MenuBar {
        Menu("Game") {
            Item("Pause", onClick = { /* toggle overlay */ })
            Item("Exit Game", onClick = { /* exit */ })
        }
    }
    App()
}
```

#### Option D: Make the hint more discoverable
- Make the "Press Esc to pause" hint stay visible for 10 seconds instead of 3
- Make it reappear every 30 seconds until the user presses Esc once
- Add an animation (gentle pulse) to draw attention

**Recommended: Combination of A + D**
- Add a persistent menu/pause icon in the corner
- Keep the hint visible longer (10s) with animation
- After first Esc press, remember the user knows about it (don't show hint again)

---

## Issue 2: SNES games stuttering

### Root Cause
The video rendering is tied to Compose's recomposition rate via `withFrameNanos`, which may not perfectly sync with the emulation thread's 60 FPS output.

**Current implementation:**
```kotlin
LaunchedEffect(controller) {
    while (true) {
        withFrameNanos { }  // <-- Waits for next Compose frame
        val frameData = controller.getVideoFrame() ?: continue
        // ... convert and display frame
    }
}
```

**Problem:**
- Compose frame rate may vary (vsync, window manager, etc.)
- If Compose runs at 59.94 Hz but emulation runs at 60 Hz, frames get dropped
- Pixel format conversion (`convertFrameInPlace`) runs on the UI thread, blocking rendering

### Solutions

#### Option A: Decouple rendering from Compose frames (Recommended)
Run frame polling in a separate coroutine at fixed rate:

```kotlin
// Add to DesktopEmulationSurface.kt
LaunchedEffect(controller) {
    while (true) {
        val frameData = controller.getVideoFrame() ?: continue
        val width = controller.getVideoWidth()
        val height = controller.getVideoHeight()
        if (width <= 0 || height <= 0) continue

        val pixelFormat = controller.getPixelFormat()
        frameBuffers.ensureCapacity(width, height, pixelFormat)
        convertFrameInPlace(frameData, width, height, pixelFormat, frameBuffers.pixelBuffer)
        frameBuffers.bitmap.installPixels(frameBuffers.imageInfo, frameBuffers.pixelBuffer, width * 4)
        currentBitmap = frameBuffers.bitmap.asComposeImageBitmap()

        // Delay based on actual emulation FPS
        val delayMs = (1000.0 / controller.getTargetFps()).toLong()
        delay(delayMs)
    }
}
```

Remove the `withFrameNanos { }` — let the Canvas just draw whatever bitmap is ready.

#### Option B: Use hardware-accelerated rendering
Replace Skia bitmap conversion with a native OpenGL/Vulkan rendering path:

- Create an OpenGL texture from libretro's video output
- Use Compose's AndroidView/UIKitView equivalent for desktop to embed native rendering
- This is how RetroArch achieves smooth rendering

This is a larger refactor but would give the best performance.

#### Option C: Move pixel conversion off UI thread
Run `convertFrameInPlace` in a background coroutine:

```kotlin
LaunchedEffect(controller) {
    while (true) {
        withContext(Dispatchers.Default) {
            val frameData = controller.getVideoFrame() ?: return@withContext
            // ... conversion logic
        }
        currentBitmap = newBitmap
        withFrameNanos { } // Wait for next frame
    }
}
```

#### Option D: Profile and optimize `convertFrameInPlace`
The pixel format conversion might be slow. Check if it can be optimized:

```kotlin
// Current implementation likely does per-pixel conversion
// Could be optimized with:
// - SIMD intrinsics
// - Bulk memory operations
// - Pre-computed lookup tables for format conversions
```

### Immediate Fix (Option A)
Replace the current frame polling loop in `DesktopEmulationSurface.kt`:

```diff
- LaunchedEffect(controller) {
-     while (true) {
-         withFrameNanos { }
-         val frameData = controller.getVideoFrame() ?: continue
-         // ...
-     }
- }

+ LaunchedEffect(controller) {
+     while (true) {
+         val frameData = controller.getVideoFrame()
+         if (frameData != null) {
+             val width = controller.getVideoWidth()
+             val height = controller.getVideoHeight()
+             if (width > 0 && height > 0) {
+                 val pixelFormat = controller.getPixelFormat()
+                 frameBuffers.ensureCapacity(width, height, pixelFormat)
+                 convertFrameInPlace(frameData, width, height, pixelFormat, frameBuffers.pixelBuffer)
+                 frameBuffers.bitmap.installPixels(frameBuffers.imageInfo, frameBuffers.pixelBuffer, width * 4)
+                 currentBitmap = frameBuffers.bitmap.asComposeImageBitmap()
+             }
+         }
+         delay(16) // ~60 FPS
+     }
+ }
```

This decouples video frame polling from Compose's recomposition rate and should eliminate stuttering.

### Testing the Fix
1. **Before fix:**
   - Start a SNES game (e.g., Super Mario World)
   - Observe FPS counter — should show drops below 60 FPS
   - Notice visual stuttering during scrolling scenes

2. **After fix:**
   - FPS should stay at 60
   - Scrolling should be smooth
   - Audio/video sync maintained

### Additional Performance Notes
- Audio playback runs in `DesktopAudioPlayer` on a separate thread — already properly decoupled
- Emulation thread runs at `MAX_PRIORITY` — good for timing accuracy
- The `Thread.sleep()` timing in the emulation loop uses nanosecond precision — acceptable

**Root cause:** The coupling of video rendering to Compose frames via `withFrameNanos` creates judder when frame rates don't align perfectly.

**Fix priority:** HIGH — this affects all games, not just SNES
