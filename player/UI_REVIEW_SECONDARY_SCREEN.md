# UI/UX Review: Secondary Screen Layout and Experience

**Reviewer:** ui-agent
**Task:** #11
**Files reviewed:**
- `player/shared/src/commonMain/.../ui/screen/SecondaryScreenContent.kt`
- `player/shared/src/commonMain/.../ui/SpelaApp.kt` (integration)
- `player/shared/src/androidMain/.../libretro/AndroidSecondaryDisplay.kt`
- `player/shared/src/androidMain/.../libretro/SecondaryDisplayPresentation.kt`

## Summary

The overall structure is solid -- correct layout order (info bar, controls, action bar), proper use of design tokens, and good lifecycle management. However, there are several issues that need to be addressed before this is ship-ready.

**Issues found: 3 critical, 4 important, 3 minor**

---

## Critical Issues

### C1. QuickActionButton touch targets are too small (40dp)
**File:** `SecondaryScreenContent.kt:202`
**Problem:** `QuickActionButton` uses `.size(40.dp)` -- below the 48dp minimum touch target size required by both our design system (`SpButton.heightIn(min = 48.dp)`) and Android accessibility guidelines. On a 3.92" screen held in landscape with thumbs, 40dp buttons will be frustrating to hit accurately.
**Fix:** Change `.size(40.dp)` to `.size(48.dp)` and increase icon size from 20.dp to 24.dp for readability.

### C2. No SpelaTheme wrapper on secondary display Presentation
**File:** `SecondaryDisplayPresentation.kt:50-52` and `AndroidSecondaryDisplay.kt:59-63`
**Problem:** The `SecondaryDisplayPresentation` sets Compose content directly without wrapping it in `SpelaTheme`. The secondary display's `ComposeView` runs in an entirely separate window with its own composition -- it does NOT inherit the theme from the primary screen's `SpelaTheme` wrapper. This means MaterialTheme colors/typography won't be set, and any Material3 components (Text with default theming, etc.) will use light theme defaults.
**Fix:** In `AndroidSecondaryDisplay.kt`, wrap the content:
```kotlin
content = {
    SpelaTheme {
        SecondaryScreenContent(
            viewModel = emulationViewModel,
            controller = libretroController,
        )
    }
}
```

### C3. No paused state handling on secondary screen
**File:** `SecondaryScreenContent.kt`
**Problem:** The task description and acceptance criteria require that when the game is paused, the secondary screen should reflect this (dim controls, show "Paused" indicator). Currently `SecondaryScreenContent` reads `state.isFastForward` and `state.fps` but never reads `state.isPaused` or `state.showOverlay`. The controls remain fully interactive even when the game is paused via the in-game overlay on the primary screen.
**Fix:** Read `state.isPaused` and `state.showOverlay`, and when either is true:
- Show a centered "PAUSED" text overlay on the touch controls area (use `SpTypography.DisplaySmall`, `SpColor.OnBackgroundSecondary`)
- Reduce touch controls opacity to ~0.3f
- Disable quick action buttons (or at minimum grey them out)

---

## Important Issues

### I1. GameInfoBar is missing play session time
**File:** `SecondaryScreenContent.kt:102-121`
**Problem:** The layout spec in task #5 shows `Game Title    00:45:12` -- the game title on the left and session elapsed time on the right. The current `GameInfoBar` only shows the game title. Session time is missing.
**Fix:** Add a session timer. `EmulationState` doesn't currently track session time, so this may require adding a `sessionStartTime` or `elapsedSeconds` field to `EmulationState` and a coroutine-based timer in `EmulationViewModel`. At minimum, add the UI slot with a placeholder "00:00" and file a follow-up task for the timer implementation.

### I2. GameInfoBar text overflow not handled with ellipsis
**File:** `SecondaryScreenContent.kt:113-118`
**Problem:** The `Text` for `gameTitle` has `maxLines = 1` but no `overflow = TextOverflow.Ellipsis`. Long game titles will be clipped abruptly without visual indication. On a 3.92" screen, many game titles will overflow.
**Fix:** Add `overflow = TextOverflow.Ellipsis` to the Text composable.

### I3. No transition animation when secondary display connects/disconnects during gameplay
**File:** `SpelaApp.kt:354-360`
**Problem:** The primary screen touch controls appear/disappear based on `!secondaryAvailable` but without any animation. The existing pattern in `PlatformTouchControls.android.kt` uses `AnimatedVisibility(fadeIn(), fadeOut())` for the physical controller detection. The same pattern should be used here for consistency.
**Fix:** Wrap the `PlatformTouchControls` block in `AnimatedVisibility`:
```kotlin
AnimatedVisibility(
    visible = emulationState.isRunning && !emulationState.showOverlay && !secondaryAvailable,
    enter = fadeIn(),
    exit = fadeOut(),
) {
    PlatformTouchControls(controller = libretroController)
}
```

### I4. Quick action buttons lack pressed/active visual feedback
**File:** `SecondaryScreenContent.kt:196-223`
**Problem:** `QuickActionButton` has an `isActive` state (used for fast-forward toggle) but no pressed state feedback. The existing `OverlayAction` in `InGameOverlay.kt` uses `MutableInteractionSource` for hover/focus states. The quick action buttons should provide immediate tactile feedback on press (alpha change, scale, or ripple).
**Fix:** Add a pressed state similar to the touch gamepad buttons (alpha 0.30 -> 0.60 on press), or use Material ripple indication. Also consider a brief scale animation (0.95f on press) matching the `SpCard` press pattern.

---

## Minor Issues

### M1. QuickActionBar vertical padding feels tight
**File:** `SecondaryScreenContent.kt:137`
**Problem:** `padding(vertical = SpSpacing.Small)` is 8dp. With 40dp (should be 48dp after C1 fix) buttons inside, the total bar height would be 64dp. On a ~960dp tall screen in portrait (or ~590dp in landscape), this is adequate, but the padding around the FPS text feels tight. Consider `SpSpacing.Medium` (12dp) for slightly more breathing room.

### M2. FPS display should use `LabelLarge` instead of `LabelMedium`
**File:** `SecondaryScreenContent.kt:179`
**Problem:** The FPS display uses `SpTypography.LabelMedium` (12sp). On a secondary screen that now has dedicated space for performance info (unlike the tiny HUD on the primary screen), this could be larger for better at-a-glance readability. `LabelLarge` (14sp) with `FontWeight.SemiBold` would make FPS more prominent, matching the intent of having a "more detailed" performance HUD on the secondary screen.

### M3. Semantics/accessibility could be enhanced on GameInfoBar
**File:** `SecondaryScreenContent.kt:102-121`
**Problem:** The `GameInfoBar` Row has no `semantics` block. While the child Text inherits content, a grouped content description like "Now playing: [gameTitle]" would be better for screen readers.

---

## What works well

- **Design token usage:** `SpColor.Background`, `SpColor.SurfaceVariant`, `SpColor.OnBackground`, `SpTypography`, `SpSpacing` -- all correctly used, no hardcoded values
- **Icon consistency:** Same `Icons.Filled.Save/FolderOpen/CameraAlt/FastForward` as `InGameOverlay` -- users see familiar icons
- **FPS color coding:** Correctly matches existing thresholds (green >= 55, yellow >= 30, red below)
- **Layout structure:** Correct Column-based layout with `weight(1f)` for touch controls area
- **Primary screen integration:** `SpelaApp.kt:356` correctly hides touch controls when `secondaryAvailable` is true
- **Lifecycle management:** `LaunchedEffect` + `DisposableEffect` pair in `SpelaApp.kt:285-298` properly manages show/dismiss
- **Dark theme base:** Root `SpColor.Background` on secondary screen content, `SurfaceVariant` for bars

---

## Verdict

**Requesting changes.** The 3 critical issues (touch target size, missing theme wrapper, no paused state) must be fixed before this can ship. The important issues (missing session time, text overflow, no animation, no press feedback) should also be addressed for a polished experience.
