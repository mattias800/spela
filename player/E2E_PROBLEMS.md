# Android E2E Test — Known Problems & Solutions

## 1. AnimatedContent breaks navigation in test mode

**Problem:** Compose test framework's `waitForIdle()` interacts badly with `AnimatedContent` transitions. New screen composables get disposed before `StateFlow` updates from `collectAsState()` propagate. The screen briefly composes, the ViewModel loads data successfully, but the composable stops observing before the state update arrives.

**Solution:** Bypass `AnimatedContent` when `LocalAnimationsEnabled=false` (test mode). Render the current screen directly with `key(route)` to force proper composable identity on navigation changes. Implemented in `SpelaApp.kt`.

## 2. UiAutomator shows stale accessibility tree after navigation

**Problem:** After Compose navigation transitions, `UiSelector().text("...")` returns elements from the **previous** screen. The accessibility tree isn't refreshed immediately. This causes false positives (finding text that's no longer visible) and false negatives (not finding text on the new screen).

**Solution:** Use Compose semantic tree (`onAllNodesWithText().fetchSemanticsNodes()`) for post-navigation assertions instead of UiAutomator. UiAutomator is still reliable for pre-navigation checks and during gameplay (where Compose APIs fail).

## 3. 60fps emulation blocks all Compose/Espresso APIs

**Problem:** During gameplay, the Choreographer runs at 60fps, keeping the main looper permanently busy. ALL Compose test APIs (`fetchSemanticsNodes()`, `performClick()`, `waitForIdle()`) call Espresso's `onIdle()` internally, which throws `AppNotIdleException` after 3 seconds.

**Solution:** During gameplay, use only:
- **UiAutomator** — bypasses Espresso, reads accessibility tree directly
- **Logcat** — `device.executeShellCommand("logcat -d -s System.out:I | grep 'Game loaded'")` for game start detection
- **Thread.sleep** — instead of `waitForIdle()` for timing

Functions that need gameplay awareness: `pressBack()`, `openOverlay()`, `tapOn()`, `startGameAndWait()`.

## 4. Emulator DNS blocked by Colima

**Problem:** When Colima (Docker) is running, it takes over port 53 on the host. The Android emulator's DNS resolver can't reach external hosts like `buildbot.libretro.com`, causing core binary downloads to fail with "Unable to resolve host."

The Spela server's `/api/cores` endpoint returns empty `downloadUrl` fields, so the app falls back to downloading directly from the libretro buildbot — which requires external DNS.

**Solution:** Pre-cache core binaries on the emulator before running tests:

```bash
# Download on host (has internet)
curl -sL "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/nestopia_libretro_android.so.zip" -o /tmp/nestopia.zip
unzip -o /tmp/nestopia.zip -d /tmp/nestopia_core

# Push to emulator (persists across app reinstalls)
adb push /tmp/nestopia_core/nestopia_libretro_android.so /data/local/tmp/
```

The `KoinResetRule` in `TestHelpers.kt` automatically copies cores from `/data/local/tmp/` into the app's `files/cores/` directory before each test.

## 5. SpSplitButton click doesn't reliably trigger onClick

**Problem:** `performClick()` on nodes inside `SpSplitButton` (Play, Resume, Download) intermittently fails to trigger the button's `onClick` handler. The semantic click action dispatches but navigation doesn't happen. This appears related to `SaveableStateProvider` composable lifecycle in test mode — the composable tree may be in an inconsistent state when the click is processed.

**Status:** Unsolved. This is the main remaining blocker for EmulationTest. When it works, the full flow succeeds (login → navigate → download → play → overlay → exit). When it doesn't, the test times out waiting for the game to start.

**Workaround ideas:**
- Add test tags to Play/Resume buttons and click by tag instead of text
- Use UiAutomator coordinate-based click as fallback
- Investigate if `SaveableStateProvider` state clearing (line ~437 in SpelaApp.kt) interferes

## 6. "Browser play" false positive on Browse button

**Problem:** `waitForText("Browse")` uses substring matching, which matches "Browser play" in the console hero banner stats — a completely different element. This causes the test to think the Browse button is visible before games have loaded.

**Solution:** Use exact text match via Compose tree: `onAllNodesWithText("Browse", substring = false)` instead of UiAutomator `textContains("Browse")`.

## 7. Game cards in LazyVerticalGrid — click doesn't navigate

**Problem:** UiAutomator `click()` on a text node inside a Compose `Card` in a `LazyVerticalGrid` doesn't trigger the card's `onClick`. UiAutomator dispatches a raw touch event at the text's coordinates, but the `LazyVerticalGrid` scroll container consumes it as a scroll gesture instead of a click.

**Solution:** Use Compose test API with `hasClickAction()` matcher to find the card node (which has the semantic `OnClick` action), not just the text child:

```kotlin
val gameMatcher = hasText("Balloon Fight", substring = true) and hasClickAction()
onAllNodes(gameMatcher)[0].performClick()
```

## Test infrastructure notes

- `KoinResetRule` (order=0) must be declared in every test class — sets `isTestMode=true` before Activity creation
- `isTestMode=true` → `LocalAnimationsEnabled=false` → disables infinite animations so Compose APIs complete in ~700ms instead of blocking
- Emulation step logging: `[Emulation] Step N/7: ...` in logcat under `System.out` tag — useful for diagnosing where startup hangs
- `E2E_SETUP` logcat tag shows core pre-caching results
- `E2E_GAMEPLAY` logcat tag shows game start detection results
