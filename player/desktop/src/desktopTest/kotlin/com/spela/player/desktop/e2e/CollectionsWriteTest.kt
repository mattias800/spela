package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameCollectionDetail
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E2E tests for collections write operations.
 * Tests: Create, Edit, Delete collection; Add to / Remove from collection.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CollectionsWriteTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun createOwnedCollectionDetail(
        id: String = "1",
        name: String = "Best NES Games",
        description: String? = "My favorite NES titles",
        isPublic: Boolean = false,
        games: List<Game> = listOf(
            Game(
                id = "1",
                title = "Castlevania",
                consoleId = "1",
                consoleName = "NES",
                fileName = "castlevania.nes",
                fileSize = 131072,
            ),
            Game(
                id = "2",
                title = "Super Mario Bros.",
                consoleId = "1",
                consoleName = "NES",
                fileName = "smb.nes",
                fileSize = 40960,
            ),
        ),
    ): GameCollectionDetail = GameCollectionDetail(
        id = id,
        userId = "1", // matches FakeAuthRepository.getCurrentUser().id
        username = "player",
        name = name,
        description = description,
        isPublic = isPublic,
        gameCount = games.size,
        games = games,
    )

    @Test
    fun createCollectionFromCollectionsScreen() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Pre-populate so empty state button doesn't conflict with dialog title
        harness.collectionRepo.myCollections = listOf(
            GameCollection(
                id = "existing",
                userId = "1",
                username = "player",
                name = "Old Collection",
                gameCount = 1,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Collections)
        )
        advance(harness)

        // Verify collection list is showing
        onNodeWithText("Old Collection").assertIsDisplayed()

        // Tap FAB to open create dialog
        onNodeWithContentDescription("Create collection").performClick()
        advanceQuick(harness)

        // Verify dialog appears — check for Name text field
        onNode(hasSetTextAction() and hasText("Name")).assertIsDisplayed()

        // Type collection name into the Name text field
        onNode(hasSetTextAction() and hasText("Name")).performTextInput("Best NES Games")
        advanceQuick(harness)

        // Submit
        onNodeWithText("Create").performClick()
        // Advance test dispatcher only (not Compose clock) to process ViewModel coroutine
        // before snackbar auto-dismiss timer fires
        harness.testDispatcher.scheduler.advanceTimeBy(2_000)
        harness.testDispatcher.scheduler.runCurrent()

        // Verify via ViewModel state: dialog dismissed and success message set
        val state = harness.collectionsViewModel.state.value
        assertEquals(false, state.showCreateDialog)
        assertEquals("Collection created", state.successMessage)
    }

    @Test
    fun editCollectionFromDetailScreen() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.collectionRepo.collectionDetail = createOwnedCollectionDetail()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.CollectionDetail("1"))
        )
        advance(harness)

        // Verify detail loaded and owner actions visible
        onNodeWithContentDescription("Edit collection").assertIsDisplayed()

        // Open edit dialog
        onNodeWithContentDescription("Edit collection").performClick()
        advanceQuick(harness)

        // Verify edit dialog appears
        onNodeWithText("Edit Collection").assertIsDisplayed()

        // Replace the pre-filled name in the text field (not the top bar heading)
        onNode(hasSetTextAction() and hasText("Best NES Games"))
            .performTextReplacement("Top SNES RPGs")
        advanceQuick(harness)

        // Save
        onNodeWithText("Save").performClick()
        harness.testDispatcher.scheduler.advanceTimeBy(2_000)
        harness.testDispatcher.scheduler.runCurrent()

        // Verify via ViewModel state: dialog dismissed and success message set
        val state = harness.collectionsViewModel.state.value
        assertEquals(false, state.showEditDialog)
        assertEquals("Collection updated", state.successMessage)
    }

    @Test
    fun deleteCollectionFromDetailScreen() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.collectionRepo.collectionDetail = createOwnedCollectionDetail()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.CollectionDetail("1"))
        )
        advance(harness)

        // Verify we're on the detail screen with owner actions
        onNodeWithContentDescription("Delete collection").assertIsDisplayed()

        // Tap delete button
        onNodeWithContentDescription("Delete collection").performClick()
        advanceQuick(harness)

        // Confirm dialog should appear — verify title
        onNodeWithText("Delete Collection").assertIsDisplayed()

        // Confirm deletion
        onNodeWithText("Delete").performClick()
        advance(harness)

        // Verify via ViewModel state: collection was deleted
        val state = harness.collectionsViewModel.state.value
        assertNull(state.selectedDetail)
        assertEquals("Collection deleted", state.successMessage)
    }

    @Test
    fun addGameToCollectionFromGameDetail() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.collectionRepo.myCollections = listOf(
            GameCollection(
                id = "1",
                userId = "1",
                username = "player",
                name = "Best NES Games",
                gameCount = 3,
            ),
        )

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Verify game detail loaded
        onNodeWithText("Castlevania").assertIsDisplayed()

        // Tap "Add to collection" button
        onNodeWithContentDescription("Add to collection").performClick()
        advanceQuick(harness)

        // Collection picker dialog should appear
        onNodeWithText("Add to Collection").assertIsDisplayed()
        onNodeWithText("Best NES Games").assertIsDisplayed()

        // Select the collection
        onNodeWithContentDescription("Best NES Games, 3 games").performClick()
        harness.testDispatcher.scheduler.advanceTimeBy(2_000)
        harness.testDispatcher.scheduler.runCurrent()

        // Verify via ViewModel state: dialog dismissed and success message set
        val state = harness.gameDetailViewModel.state.value
        assertEquals(false, state.showAddToCollectionDialog)
        assertEquals("Added to Best NES Games", state.successMessage)
    }

    @Test
    fun removeGameFromCollectionDetail() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.collectionRepo.collectionDetail = createOwnedCollectionDetail()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.CollectionDetail("1"))
        )
        advance(harness)

        // Verify both games are shown
        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("Super Mario Bros.").assertIsDisplayed()

        // Remove Castlevania from collection (owner-only X button)
        onNodeWithContentDescription("Remove Castlevania from collection").performClick()
        // Advance test dispatcher for optimistic update + async completion
        harness.testDispatcher.scheduler.advanceTimeBy(2_000)
        harness.testDispatcher.scheduler.runCurrent()

        // Verify via ViewModel state: game removed and success message set
        val state = harness.collectionsViewModel.state.value
        assertEquals("Removed from collection", state.successMessage)
        assertTrue(
            state.selectedDetail?.games?.none { it.id == "1" } == true,
            "Castlevania should be removed from collection detail",
        )

        // Advance Compose to update UI, then verify UI changes
        advance(harness)
        onNodeWithText("Castlevania").assertDoesNotExist()
        onNodeWithText("Super Mario Bros.").assertIsDisplayed()
    }

    @Test
    fun gameDetailShowsAddToCollectionButton() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Verify game detail loaded
        onNodeWithText("Castlevania").assertIsDisplayed()

        // "Add to collection" button should be visible
        onNodeWithContentDescription("Add to collection").assertIsDisplayed()
    }
}
