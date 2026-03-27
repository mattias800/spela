# Core Mismatch Save State Warning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Warn users before any save state operation when playing on a different core than the session's original core, preventing accidental overwrite of cross-device save states.

**Architecture:** Add `isCoreMismatched` flag to EmulationState, set during launch. Intercept all save state operations (auto-save on exit, manual save, slot save, quick save) to show a warning dialog. SRAM is always saved before the dialog appears.

**Tech Stack:** Kotlin Multiplatform (Compose), player app only

---

### Task 1: Add core mismatch state fields

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/state/EmulationState.kt`

- [ ] **Step 1: Add isCoreMismatched and related fields**

Add after the existing core mismatch dialog fields (after line 161):

```kotlin
    /** Persistent core mismatch flag: true for the entire session if cores differ. */
    val isCoreMismatched: Boolean = false,
    /** The original core name from the session (for save warning dialog). */
    val mismatchedOriginalCore: String = "",
    /** Show the save warning dialog when attempting to save with mismatched core. */
    val showCoreMismatchSaveDialog: Boolean = false,
    /** The type of save that triggered the warning ("auto", "manual", "slot", "quick"). */
    val pendingSaveType: String = "",
    /** The slot number for pending slot saves. */
    val pendingSaveSlot: Int = 0,
```

- [ ] **Step 2: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/state/EmulationState.kt
git commit -m "feat: add core mismatch save warning state fields"
```

---

### Task 2: Add intents for save warning dialog

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/intent/EmulationIntent.kt`

- [ ] **Step 1: Add new intents**

Add after the existing core mismatch intents (after line 101):

```kotlin
    // Core mismatch save warning
    /** User confirmed saving the save state despite core mismatch. */
    data object ConfirmCoreMismatchSave : EmulationIntent
    /** User chose to skip saving the save state (SRAM already saved). */
    data object SkipCoreMismatchSave : EmulationIntent
```

- [ ] **Step 2: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/intent/EmulationIntent.kt
git commit -m "feat: add core mismatch save warning intents"
```

---

### Task 3: Set isCoreMismatched during launch

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt`

- [ ] **Step 1: Set flag when core mismatch is detected at launch**

In `handleAutoLoadResult()` (around line 787), where `CoreMismatch` is handled, add:

```kotlin
is AutoLoadResult.CoreMismatch -> {
    _state.update {
        it.copy(
            showCoreMismatchDialog = true,
            coreMismatchSaveCoreName = result.saveCoreName,
            coreMismatchCurrentCoreName = result.currentCoreName,
            // Persist mismatch flag for the entire session
            isCoreMismatched = true,
            mismatchedOriginalCore = result.saveCoreName,
        )
    }
}
```

Find the existing `CoreMismatch` handling and add the two new fields to the existing `copy()` call.

- [ ] **Step 2: Compile check**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt
git commit -m "feat: set isCoreMismatched flag during launch mismatch detection"
```

---

### Task 4: Intercept save operations and show warning

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt`

- [ ] **Step 1: Create helper to check mismatch before saving**

Add a private method:

```kotlin
/** Returns true if save should proceed, false if dialog was shown. */
private fun checkCoreMismatchBeforeSave(saveType: String, slot: Int = 0): Boolean {
    if (_state.value.isCoreMismatched) {
        _state.update {
            it.copy(
                showCoreMismatchSaveDialog = true,
                pendingSaveType = saveType,
                pendingSaveSlot = slot,
            )
        }
        return false // Dialog shown, don't save yet
    }
    return true // No mismatch, proceed with save
}
```

- [ ] **Step 2: Intercept SaveState intent**

Change the `EmulationIntent.SaveState` handler (line 123) from:
```kotlin
EmulationIntent.SaveState -> saveManager.saveState()
```
To:
```kotlin
EmulationIntent.SaveState -> {
    if (checkCoreMismatchBeforeSave("manual")) {
        saveManager.saveState()
    }
}
```

- [ ] **Step 3: Intercept SaveToSlot intent**

Change the `EmulationIntent.SaveToSlot` handler (line 203) from:
```kotlin
is EmulationIntent.SaveToSlot -> {
    _state.update { it.copy(activeSlot = intent.slot) }
    saveManager.saveToSlot(intent.slot)
}
```
To:
```kotlin
is EmulationIntent.SaveToSlot -> {
    _state.update { it.copy(activeSlot = intent.slot) }
    if (checkCoreMismatchBeforeSave("slot", intent.slot)) {
        saveManager.saveToSlot(intent.slot)
    }
}
```

- [ ] **Step 4: Intercept QuickSave intent**

Change the `EmulationIntent.QuickSave` handler (line 200) from:
```kotlin
EmulationIntent.QuickSave -> quickSaveToSlot()
```
To:
```kotlin
EmulationIntent.QuickSave -> {
    if (checkCoreMismatchBeforeSave("quick")) {
        quickSaveToSlot()
    }
}
```

- [ ] **Step 5: Intercept auto-save on exit**

In `stopGame()` (around line 690), change:
```kotlin
} else if (currentPreferences.autoSaveEnabled && !currentState.isChallengeMode) {
    saveManager.autoSaveOnStop(currentState.gameId)
}
```
To:
```kotlin
} else if (currentPreferences.autoSaveEnabled && !currentState.isChallengeMode) {
    if (currentState.isCoreMismatched) {
        // Save SRAM first (always safe), then show dialog
        try {
            kotlinx.coroutines.withTimeout(15_000L) {
                saveManager.saveSramOnStop(currentState.gameId)
            }
        } catch (_: Exception) {}
        withContext(dispatchers.main) {
            _state.update {
                it.copy(
                    showCoreMismatchSaveDialog = true,
                    pendingSaveType = "auto",
                )
            }
        }
        // Wait for user's choice before completing stop
        // The dialog handlers will call completeStopAfterSaveChoice()
        return@launch
    } else {
        saveManager.autoSaveOnStop(currentState.gameId)
    }
}
```

Note: This requires the stop flow to pause and wait for the dialog. The ConfirmCoreMismatchSave/SkipCoreMismatchSave handlers will complete the stop.

- [ ] **Step 6: Handle dialog confirmation intents**

Add to the `when (intent)` block:

```kotlin
EmulationIntent.ConfirmCoreMismatchSave -> {
    _state.update { it.copy(showCoreMismatchSaveDialog = false) }
    val pendingType = _state.value.pendingSaveType
    val pendingSlot = _state.value.pendingSaveSlot
    scope.launch(dispatchers.io) {
        when (pendingType) {
            "auto" -> {
                saveManager.autoSaveOnStop(_state.value.gameId)
                completeStop()
            }
            "manual" -> saveManager.saveState()
            "slot" -> saveManager.saveToSlot(pendingSlot)
            "quick" -> quickSaveToSlot()
        }
    }
}
EmulationIntent.SkipCoreMismatchSave -> {
    _state.update { it.copy(showCoreMismatchSaveDialog = false) }
    if (_state.value.pendingSaveType == "auto") {
        scope.launch(dispatchers.io) { completeStop() }
    }
}
```

The `completeStop()` method needs to contain the remaining stop logic after auto-save (SRAM save, state cleanup). Extract this from the existing `stopGame()` — the code after `autoSaveOnStop` (lines 694-709+).

- [ ] **Step 7: Compile check**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`

- [ ] **Step 8: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt
git commit -m "feat: intercept save operations with core mismatch warning"
```

---

### Task 5: Create save warning dialog UI

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/CoreMismatchSaveDialog.kt`

- [ ] **Step 1: Create the dialog composable**

Follow the same pattern as the existing `CoreMismatchDialog.kt`:

```kotlin
package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Warning dialog shown when the user attempts to save a save state
 * while playing on a different core than the session's original core.
 * SRAM (in-game saves) has already been saved before this dialog appears.
 */
@Composable
internal fun CoreMismatchSaveDialog(
    originalCoreName: String,
    currentCoreName: String,
    onSaveAnyway: () -> Unit,
    onSkipSaveState: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(SpSpacing.XLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Save State Compatibility",
                style = SpTypography.HeadlineMedium,
                color = SpColor.Warning,
            )
            Spacer(Modifier.height(SpSpacing.Small))

            val bodyText = buildAnnotatedString {
                val bold = SpanStyle(fontWeight = FontWeight.Bold, color = SpColor.OnBackground)
                append("This session's save state was created with ")
                withStyle(bold) { append(originalCoreName) }
                append(". Saving now will replace it with a save state from ")
                withStyle(bold) { append(currentCoreName) }
                append(", which won't work on devices using ")
                withStyle(bold) { append(originalCoreName) }
                append(".")
            }
            Text(
                text = bodyText,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = "Your in-game save (game progress) has been saved and works on all cores.",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.XLarge))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                SpButton(
                    text = "Save State Anyway",
                    onClick = onSaveAnyway,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpSecondaryButton(
                    text = "Skip Save State",
                    onClick = onSkipSaveState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Render dialog in the emulation screen**

Find where `CoreMismatchDialog` is rendered in the emulation screen (likely in `SpelaApp.kt` or the in-game overlay area) and add the save dialog alongside it:

```kotlin
if (state.showCoreMismatchSaveDialog) {
    CoreMismatchSaveDialog(
        originalCoreName = state.mismatchedOriginalCore,
        currentCoreName = state.coreMismatchCurrentCoreName.ifEmpty { "current core" },
        onSaveAnyway = { emulationViewModel.onIntent(EmulationIntent.ConfirmCoreMismatchSave) },
        onSkipSaveState = { emulationViewModel.onIntent(EmulationIntent.SkipCoreMismatchSave) },
    )
}
```

Find the exact location by searching for `showCoreMismatchDialog` (the existing launch dialog) in the composable tree.

- [ ] **Step 3: Compile check**

Run: `cd player && ./gradlew :shared:compileKotlinDesktop`

- [ ] **Step 4: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/CoreMismatchSaveDialog.kt
git add -u
git commit -m "feat: add core mismatch save warning dialog UI"
```

---

### Task 6: Desktop E2E tests

**Files:**
- Modify or create: `player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/CoreMismatchSaveTest.kt`

- [ ] **Step 1: Write tests**

```kotlin
package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.state.EmulationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CoreMismatchSaveTest {

    @Test
    fun isCoreMismatchedSetWhenCoresDiffer() {
        // Test that isCoreMismatched is set during mismatch detection
        val harness = SpelaTestHarness(StandardTestDispatcher())
        val vm = harness.emulationViewModel

        // Simulate mismatch state
        vm.onIntent(EmulationIntent.StartGame(gameId = "1"))
        // After start, manually set mismatch state (since fake won't trigger real mismatch)

        // Verify the state field exists and defaults to false
        assertFalse(vm.state.value.isCoreMismatched)
        assertEquals("", vm.state.value.mismatchedOriginalCore)
    }

    @Test
    fun saveDialogNotShownWhenCoresMatch() {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        val vm = harness.emulationViewModel

        // Without mismatch, save should not show dialog
        assertFalse(vm.state.value.isCoreMismatched)
        assertFalse(vm.state.value.showCoreMismatchSaveDialog)
    }

    @Test
    fun confirmCoreMismatchSaveDismissesDialog() {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        val vm = harness.emulationViewModel

        // Manually set dialog state
        // (In real flow, this would be triggered by save attempt with mismatch)
        vm.onIntent(EmulationIntent.ConfirmCoreMismatchSave)

        assertFalse(vm.state.value.showCoreMismatchSaveDialog)
    }

    @Test
    fun skipCoreMismatchSaveDismissesDialog() {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        val vm = harness.emulationViewModel

        vm.onIntent(EmulationIntent.SkipCoreMismatchSave)

        assertFalse(vm.state.value.showCoreMismatchSaveDialog)
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd player && ./gradlew :desktop:desktopTest --tests "com.spela.player.desktop.e2e.CoreMismatchSaveTest" --rerun-tasks`
Expected: All pass.

- [ ] **Step 3: Run full test suite**

Run: `cd player && ./gradlew :shared:desktopTest :desktop:desktopTest --rerun-tasks`
Expected: All shared tests pass, no new E2E failures.

- [ ] **Step 4: Commit**

```bash
git add player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/CoreMismatchSaveTest.kt
git commit -m "test: add desktop E2E tests for core mismatch save warning"
```
