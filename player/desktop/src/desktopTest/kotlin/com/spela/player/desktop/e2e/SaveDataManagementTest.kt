package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.SaveDataIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E2E tests for Save Data Management screen (Phase 6/8).
 * Tests: save data list rendering, rename dialog, delete confirmation, empty state.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SaveDataManagementTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun saveDataScreenShowsEmptyState() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SaveDataManagement("1"))
        )
        advanceFully(harness)

        onNodeWithText("No save data").assertIsDisplayed()
    }

    @Test
    fun saveDataScreenShowsSaveDataList() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.saveDataRepo.preAddSaveData(id = 1, gameId = 1, name = "Main Save", isActive = true)
        harness.saveDataRepo.preAddSaveData(id = 2, gameId = 1, name = "Backup Save", isActive = false)

        setContent { harness.App() }
        advance(harness)

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SaveDataManagement("1"))
        )
        advanceFully(harness)

        onNodeWithText("Main Save").assertIsDisplayed()
        onNodeWithText("Active").assertIsDisplayed()
        onNodeWithText("Backup Save").assertIsDisplayed()
    }

    @Test
    fun saveDataRenameDialogAppears() = runComposeUiTest {
        val harness = createLoggedInHarness()
        val sd = harness.saveDataRepo.preAddSaveData(id = 1, gameId = 1, name = "My Save")

        setContent { harness.App() }
        advance(harness)

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SaveDataManagement("1"))
        )
        advanceFully(harness)

        // Click rename button
        onAllNodesWithText("Rename").onFirst().performClick()
        advanceQuick(harness)

        onNodeWithText("Rename Save Data").assertIsDisplayed()
    }

    @Test
    fun saveDataDeleteConfirmationAppears() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.saveDataRepo.preAddSaveData(id = 1, gameId = 1, name = "My Save")

        setContent { harness.App() }
        advance(harness)

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SaveDataManagement("1"))
        )
        advanceFully(harness)

        // Click delete button
        onAllNodesWithText("Delete").onFirst().performClick()
        advanceQuick(harness)

        onNodeWithText("Delete Save Data").assertIsDisplayed()
    }

    @Test
    fun saveDataDeleteConfirmationDismisses() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.saveDataRepo.preAddSaveData(id = 1, gameId = 1, name = "My Save")

        setContent { harness.App() }
        advance(harness)

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.SaveDataManagement("1"))
        )
        advanceFully(harness)

        // Click delete, then cancel
        onAllNodesWithText("Delete").onFirst().performClick()
        advanceQuick(harness)

        onNodeWithText("Delete Save Data").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        advanceQuick(harness)

        onNodeWithText("Delete Save Data").assertDoesNotExist()
    }
}
