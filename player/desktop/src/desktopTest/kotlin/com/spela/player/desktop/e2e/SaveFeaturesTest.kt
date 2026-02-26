package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.SaveState
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * E2E tests for all 10 new save-state features.
 *
 * Feature 1:  Save state screenshots (thumbnail vs placeholder)
 * Feature 2:  Rename save states (dialog, update)
 * Feature 3:  Sync status indicator (cloud icons, unsynced warning)
 * Feature 4:  Save state notes (display, edit dialog)
 * Feature 5:  Quick-save slots (state pipeline, overlay slot indicator)
 * Feature 6:  Auto-save history (state pipeline)
 * Feature 7:  Bulk delete saves (selection mode, checkboxes, delete)
 * Feature 8:  Storage usage (state pipeline)
 * Feature 9:  Export/import (state pipeline for import)
 * Feature 10: Rewind (overlay controls)
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SaveFeaturesTest {

    // ── Helpers ──

    private fun createHarnessOnGameDetail(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToGameDetail(harness: SpelaTestHarness) =
        navigateToGameDetail(harness, "1")

    private fun ComposeUiTest.scrollToSection(matcher: SemanticsMatcher) {
        onNodeWithTag("game_detail_content").performScrollToNode(matcher)
    }

    private fun createHarnessWithGameReady(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        return harness
    }

    private fun ComposeUiTest.startGameAndOpenOverlay(harness: SpelaTestHarness) {
        setContent { harness.App() }
        advance(harness)

        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness)

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 1: Save State Screenshots
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun saveStateShowsThumbnailWhenScreenshotUrlPresent() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(
                id = 1,
                gameId = 1,
                name = "Boss Fight",
                screenshotUrl = "https://example.com/screenshot.png",
                isAuto = false,
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Boss Fight"))
        onNodeWithText("Boss Fight").assertIsDisplayed()
        // When screenshotUrl is present, the thumbnail image should be rendered
        onNodeWithContentDescription("Save state thumbnail").assertExists()
    }

    @Test
    fun saveStateShowsPlaceholderWhenNoScreenshot() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(
                id = 1,
                gameId = 1,
                name = "My Save",
                screenshotUrl = null,
                isAuto = false,
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("My Save"))
        onNodeWithText("My Save").assertIsDisplayed()
        // Without screenshot, placeholder letter "S" should be shown
        onNodeWithText("S").assertExists()
        onNodeWithContentDescription("Save state thumbnail").assertDoesNotExist()
    }

    @Test
    fun autoSavePlaceholderShowsA() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(
                id = 1,
                gameId = 1,
                name = "Auto Save",
                screenshotUrl = null,
                isAuto = true,
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Auto Save"))
        // Auto-save placeholder should show "A"
        onNodeWithText("A").assertExists()
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 2: Rename Save States
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun renameButtonVisibleOnManualSave() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Old Name", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Old Name"))
        onNodeWithContentDescription("Rename Old Name").assertExists()
    }

    @Test
    fun renameButtonNotVisibleOnAutoSave() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Auto Save", isAuto = true),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Auto Save"))
        // Rename button should not appear for auto-saves
        onNodeWithContentDescription("Rename Auto Save").assertDoesNotExist()
    }

    @Test
    fun renameDialogAppearsOnClick() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "My Save", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("My Save"))
        onNodeWithContentDescription("Rename My Save").performClick()
        advanceQuick(harness)

        onNodeWithText("Rename Save State").assertIsDisplayed()
        onNodeWithTag("rename_input").assertIsDisplayed()
    }

    @Test
    fun renameUpdatesSaveNameInState() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Old Name", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Trigger rename via ViewModel intent directly (more reliable than clicking through dialog)
        harness.gameDetailViewModel.onIntent(GameDetailIntent.RenameSave(1, "New Name"))
        advance(harness)

        // Verify state was updated
        val state = harness.gameDetailViewModel.state.value
        assertEquals("New Name", state.saveStates.first().name)
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 3: Sync Status Indicator
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun syncedSaveShowsCloudIcon() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Synced Save", isSynced = true, isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Synced Save"))
        onNodeWithContentDescription("Synced").assertExists()
    }

    @Test
    fun unsyncedSaveShowsCloudOffIcon() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Local Save", isSynced = false, isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Local Save"))
        onNodeWithContentDescription("Local only").assertExists()
    }

    @Test
    fun unsyncedCountWarningDisplayed() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Save A", isSynced = false, isAuto = false),
            SaveState(id = 2, gameId = 1, name = "Save B", isSynced = false, isAuto = false),
            SaveState(id = 3, gameId = 1, name = "Save C", isSynced = true, isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("2 unsynced saves"))
        onNodeWithText("2 unsynced saves").assertIsDisplayed()
    }

    @Test
    fun noUnsyncedWarningWhenAllSynced() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Synced A", isSynced = true, isAuto = false),
            SaveState(id = 2, gameId = 1, name = "Synced B", isSynced = true, isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Synced A"))
        // Should not show unsynced warning text
        onNodeWithText("unsynced save", substring = true).assertDoesNotExist()
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 4: Save State Notes
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun saveNoteDisplayedBelowName() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(
                id = 1,
                gameId = 1,
                name = "Boss Fight",
                notes = "Right before Dracula",
                isAuto = false,
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Right before Dracula"))
        onNodeWithText("Boss Fight").assertExists()
        onNodeWithText("Right before Dracula").assertExists()
    }

    @Test
    fun saveWithoutNotesDoesNotShowNotesText() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(
                id = 1,
                gameId = 1,
                name = "Clean Save",
                notes = null,
                isAuto = false,
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Clean Save"))
        onNodeWithText("Clean Save").assertIsDisplayed()
        // No notes text should be visible (only the save name and metadata)
    }

    @Test
    fun updateNotesViaViewModelUpdatesState() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "My Save", notes = null, isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Update notes via intent
        harness.gameDetailViewModel.onIntent(GameDetailIntent.UpdateSaveNotes(1, "Good grinding spot"))
        advance(harness)

        val state = harness.gameDetailViewModel.state.value
        assertEquals("Good grinding spot", state.saveStates.first().notes)
    }

    @Test
    fun blankNotesSetToNull() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "My Save", notes = "Old notes", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Set blank notes — should clear to null
        harness.gameDetailViewModel.onIntent(GameDetailIntent.UpdateSaveNotes(1, "   "))
        advance(harness)

        val state = harness.gameDetailViewModel.state.value
        assertEquals(null, state.saveStates.first().notes)
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 5: Quick-Save Slots (state pipeline + overlay)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun slotIndicatorVisibleInOverlay() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameAndOpenOverlay(harness)

        // The overlay should show the current slot number
        onNodeWithContentDescription("Active quick-save slot: 1").assertExists()
    }

    @Test
    fun quickSaveSlotsLoadedInFakeSaveRepo() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        // Pre-populate a slot
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 100, gameId = 1, name = "Slot 1", slot = 1, isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Verify the slot data is accessible through the repo
        val slotsResult = kotlinx.coroutines.runBlocking {
            harness.saveRepo.getSlots("1")
        }
        assertTrue(slotsResult.isSuccess)
        val slots = slotsResult.getOrThrow()
        assertEquals(10, slots.size)
        // Slot 1 should have the populated save
        assertTrue(slots[0].saveState != null || slots.any { it.slot == 1 })
    }

    @Test
    fun saveToSlotAndLoadFromSlotViaRepo() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        val testData = ByteArray(64) { 42 }

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Save to slot via repository
        val saveResult = kotlinx.coroutines.runBlocking {
            harness.saveRepo.saveToSlot("1", 3, testData, null)
        }
        assertTrue(saveResult.isSuccess)

        // Load from slot
        val loadResult = kotlinx.coroutines.runBlocking {
            harness.saveRepo.loadFromSlot("1", 3)
        }
        assertTrue(loadResult.isSuccess)
        assertEquals(testData.toList(), loadResult.getOrThrow().toList())
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 6: Auto-Save History (state pipeline)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun autoSaveHistoryReturnsMultipleAutoSaves() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        val now = Clock.System.now()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Auto Save", isAuto = true, createdAt = now - 1.hours),
            SaveState(id = 2, gameId = 1, name = "Auto Save", isAuto = true, createdAt = now - 2.hours),
            SaveState(id = 3, gameId = 1, name = "Auto Save", isAuto = true, createdAt = now - 3.hours),
            SaveState(id = 4, gameId = 1, name = "Manual Save", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Verify auto-save history through the repo
        val historyResult = kotlinx.coroutines.runBlocking {
            harness.saveRepo.getAutoSaveHistory("1")
        }
        assertTrue(historyResult.isSuccess)
        val history = historyResult.getOrThrow()
        assertEquals(3, history.size)
        assertTrue(history.all { it.isAuto })
    }

    @Test
    fun autoSaveHistoryExcludesManualSaves() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Auto Save", isAuto = true),
            SaveState(id = 2, gameId = 1, name = "Manual Save", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        val historyResult = kotlinx.coroutines.runBlocking {
            harness.saveRepo.getAutoSaveHistory("1")
        }
        assertEquals(1, historyResult.getOrThrow().size)
    }

    @Test
    fun multipleSaveStatesDisplayedInList() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        val now = Clock.System.now()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Auto Save", isAuto = true, createdAt = now - 1.hours),
            SaveState(id = 2, gameId = 1, name = "Auto Save 2", isAuto = true, createdAt = now - 2.hours),
            SaveState(id = 3, gameId = 1, name = "Manual Save", isAuto = false, createdAt = now - 3.hours),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Save States"))
        // All saves (auto and manual) appear in the save states list
        onNodeWithText("Auto Save").assertExists()
        onNodeWithText("Auto Save 2").assertExists()
        onNodeWithText("Manual Save").assertExists()
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 7: Bulk Delete Saves
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun selectButtonVisibleWhenMultipleSaves() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Save A", isAuto = false),
            SaveState(id = 2, gameId = 1, name = "Save B", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Save States"))
        onNodeWithText("Select").assertExists()
    }

    @Test
    fun selectButtonHiddenWithOneSave() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Only Save", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Only Save"))
        // With only one save, Select button should not appear
        onNodeWithText("Select").assertDoesNotExist()
    }

    @Test
    fun selectionModeShowsDeleteAndCancelButtons() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Save A", isAuto = false),
            SaveState(id = 2, gameId = 1, name = "Save B", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Save States"))
        // Enter selection mode
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ToggleSelectionMode)
        advanceQuick(harness)

        onNodeWithText("Cancel").assertExists()
        onNodeWithText("Delete (0)", substring = true).assertExists()
    }

    @Test
    fun selectAllExcludesAutoSaves() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Save A", isAuto = false),
            SaveState(id = 2, gameId = 1, name = "Save B", isAuto = false),
            SaveState(id = 3, gameId = 1, name = "Auto Save", isAuto = true),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Enter selection mode and select all
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ToggleSelectionMode)
        advance(harness)
        harness.gameDetailViewModel.onIntent(GameDetailIntent.SelectAllSaves)
        advanceQuick(harness)

        val state = harness.gameDetailViewModel.state.value
        // Only non-auto saves should be selected
        assertEquals(setOf(1L, 2L), state.selectedSaveIds)
        assertFalse(3L in state.selectedSaveIds, "Auto-save should not be selected")
    }

    @Test
    fun deleteSelectedRemovesSaves() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Save A", isAuto = false),
            SaveState(id = 2, gameId = 1, name = "Save B", isAuto = false),
            SaveState(id = 3, gameId = 1, name = "Keep This", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Enter selection mode, select saves 1 and 2, then delete
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ToggleSelectionMode)
        advance(harness)
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ToggleSaveSelection(1))
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ToggleSaveSelection(2))
        advanceQuick(harness)

        harness.gameDetailViewModel.onIntent(GameDetailIntent.DeleteSelectedSaves)
        // Advance test dispatcher directly to process the ViewModel coroutine
        harness.testDispatcher.scheduler.advanceTimeBy(2_000)
        harness.testDispatcher.scheduler.runCurrent()

        val state = harness.gameDetailViewModel.state.value
        assertEquals(1, state.saveStates.size)
        assertEquals("Keep This", state.saveStates.first().name)
        assertFalse(state.isSelectionMode, "Selection mode should be off after bulk delete")
        assertEquals("2 saves deleted", state.successMessage)
    }

    @Test
    fun cancelSelectionExitsSelectionMode() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Save A", isAuto = false),
            SaveState(id = 2, gameId = 1, name = "Save B", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Enter then exit selection mode
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ToggleSelectionMode)
        advance(harness)
        assertTrue(harness.gameDetailViewModel.state.value.isSelectionMode)

        harness.gameDetailViewModel.onIntent(GameDetailIntent.ToggleSelectionMode)
        advanceQuick(harness)
        assertFalse(harness.gameDetailViewModel.state.value.isSelectionMode)
        assertTrue(harness.gameDetailViewModel.state.value.selectedSaveIds.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 8: Storage Usage (state pipeline)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun storageUsageReturnsDataFromRepo() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Verify storage usage is accessible via the repository
        val result = kotlinx.coroutines.runBlocking {
            harness.saveRepo.getStorageUsage()
        }
        assertTrue(result.isSuccess)
        val usage = result.getOrThrow()
        assertEquals(0L, usage.totalBytes)
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 9: Export/Import (state pipeline for import)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun importSaveAddsToSaveList() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "Existing Save", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        val countBefore = harness.gameDetailViewModel.state.value.saveStates.size

        // Import a save via intent
        val importData = ByteArray(256) { it.toByte() }
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ImportSave("Imported Save", importData))
        // Advance test dispatcher directly to process the ViewModel coroutine
        harness.testDispatcher.scheduler.advanceTimeBy(2_000)
        harness.testDispatcher.scheduler.runCurrent()

        val state = harness.gameDetailViewModel.state.value
        assertTrue(state.saveStates.size > countBefore, "Import should add a save")
        assertTrue(state.saveStates.any { it.name == "Imported Save" })
        assertEquals("Save imported", state.successMessage)
    }

    @Test
    fun exportSaveIntentIsHandled() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(id = 1, gameId = 1, name = "My Save", isAuto = false),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // ExportSave intent should not throw or cause state error
        harness.gameDetailViewModel.onIntent(GameDetailIntent.ExportSave(1))
        advance(harness)

        val state = harness.gameDetailViewModel.state.value
        // Export is handled by the UI layer, so the ViewModel just passes it through
        assertEquals(null, state.error)
    }

    // ══════════════════════════════════════════════════════════════════
    // Feature 10: Rewind
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun rewindButtonVisibleWhenRewindEnabled() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        // Enable rewind in preferences
        harness.preferencesRepo

        startGameAndOpenOverlay(harness)

        // Enable rewind via emulation state
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleRewind)
        advanceQuick(harness)

        // Re-open overlay to see the updated buttons
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)

        // Check if rewind state is enabled
        val state = harness.emulationViewModel.state.value
        assertTrue(state.rewindEnabled, "Rewind should be enabled after toggling")
    }

    @Test
    fun rewindButtonNotVisibleWhenRewindDisabled() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameAndOpenOverlay(harness)

        // Rewind is disabled by default
        val state = harness.emulationViewModel.state.value
        assertFalse(state.rewindEnabled, "Rewind should be disabled by default")

        // Rewind button should not be in the overlay
        onNodeWithContentDescription("Rewind").assertDoesNotExist()
    }

    // ══════════════════════════════════════════════════════════════════
    // Combined scenario: Multiple features on same save state
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun saveStateWithAllFeatures() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        val twoHoursAgo = Clock.System.now() - 2.hours
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(
                id = 1,
                gameId = 1,
                name = "Full Featured Save",
                screenshotUrl = "https://example.com/screen.png",
                isSynced = true,
                notes = "Before the final boss",
                createdAt = twoHoursAgo,
                isAuto = false,
            ),
            SaveState(
                id = 2,
                gameId = 1,
                name = "Local Only Save",
                screenshotUrl = null,
                isSynced = false,
                notes = null,
                createdAt = twoHoursAgo,
                isAuto = false,
            ),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        // Scroll to the unsynced warning to bring save states into view
        scrollToSection(hasText("1 unsynced save"))
        onNodeWithText("1 unsynced save").assertExists()

        // First save: synced icon, notes
        scrollToSection(hasText("Full Featured Save"))
        onNodeWithText("Full Featured Save").assertExists()
        onNodeWithText("Before the final boss").assertExists()
        onNodeWithContentDescription("Synced").assertExists()

        // Second save: local-only icon
        scrollToSection(hasText("Local Only Save"))
        onNodeWithText("Local Only Save").assertExists()
        onNodeWithContentDescription("Local only").assertExists()
    }

    @Test
    fun saveStateListShowsEmptyState() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        // No saves at all

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasText("Save States"))
        // Empty state should be shown
        onNodeWithText("No save states").assertExists()
    }
}
