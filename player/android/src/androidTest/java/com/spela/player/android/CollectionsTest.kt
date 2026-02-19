package com.spela.player.android

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E tests for collections write operations.
 *
 * Tests create, edit, delete collections, add/remove games,
 * public toggle, and ownership-based visibility of edit/delete buttons.
 *
 * Prerequisites:
 * - Server running with seeded data (player/player123 and admin/admin123 users)
 * - Device connected and unlocked
 */
@RunWith(AndroidJUnit4::class)
class CollectionsTest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    // ── Navigation helpers ──

    private fun navigateToCollectionsTab() {
        rule.tapOn("Library")
        rule.waitForText("Collections", timeout = 5_000)
        rule.tapOn("Collections")
        rule.waitForText("My Collections", timeout = 5_000)
    }

    /**
     * Create a collection via the FAB on the Collections tab.
     * Returns with the Collections tab visible and the new collection in the list.
     */
    private fun createCollection(
        name: String,
        description: String? = null,
        isPublic: Boolean = false,
    ) {
        rule.onNodeWithContentDescription("Create collection", substring = true).performClick()
        rule.waitForIdle()
        rule.waitForText("Create Collection", timeout = 5_000)

        rule.onNode(hasText("Name") and hasSetTextAction())
            .performTextInput(name)

        if (description != null) {
            rule.onNode(hasText("Description") and hasSetTextAction())
                .performTextInput(description)
        }

        if (isPublic) {
            rule.tapOn("Make public")
        }

        rule.onNodeWithText("Create").performClick()
        rule.waitForText("Collection created", timeout = 8_000)

        // Wait for list to refresh with the new collection
        rule.waitForText(name, timeout = 8_000)
    }

    /**
     * Ensure a collection with the given name exists. Creates it if not found.
     * Returns on the Collections tab.
     */
    private fun ensureCollectionExists(
        name: String,
        description: String? = null,
        isPublic: Boolean = false,
    ) {
        navigateToCollectionsTab()
        val exists = rule.onAllNodesWithText(name, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (!exists) {
            createCollection(name, description, isPublic)
        }
    }

    /**
     * Navigate into a collection by name from the Collections tab.
     * Assumes the Collections tab is visible.
     */
    private fun openCollection(name: String) {
        rule.scrollToAndTapText(name)
        // Wait for the collection detail screen — check for the collection name in top bar
        rule.waitForText(name, timeout = 8_000)
        // Wait a beat for the detail to fully load
        Thread.sleep(500)
    }

    /**
     * Delete the currently-opened collection via the delete button in the top bar.
     * Assumes we're on CollectionDetailScreen with isOwner=true.
     * Returns on the Collections tab after deletion.
     */
    private fun deleteCurrentCollection() {
        // Click the delete icon button in the top bar
        rule.waitForContentDescription("Delete collection", timeout = 5_000)
        rule.onNodeWithContentDescription("Delete collection", substring = true).performClick()
        rule.waitForIdle()
        Thread.sleep(500)

        // Wait for the confirmation dialog
        rule.waitForText("cannot be undone", timeout = 5_000)

        // Click the "Delete" confirm button (exact match, NOT substring).
        // tapOn() uses substring=true which would match "Delete Collection" title first.
        rule.onNodeWithText("Delete").performClick()
        rule.waitForIdle()

        // After successful delete, the LaunchedEffect in CollectionDetailScreen
        // calls onBack(), navigating back to LibraryScreen. The library may restore
        // to the Consoles tab (default) rather than the Collections tab, because
        // AnimatedContent disposes the old composition and rememberSaveable may
        // not preserve the tab index. Wait for the detail screen controls to
        // disappear (selectedDetail becomes null after delete success).
        rule.waitForNotVisible("Delete collection", timeout = 15_000)

        // Navigate to the Collections tab if not already there
        val hasMyCollections = rule.onAllNodesWithText("My Collections", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (!hasMyCollections) {
            rule.waitForText("Collections", timeout = 5_000)
            rule.tapOn("Collections")
            rule.waitForText("My Collections", timeout = 5_000)
        }
    }

    /**
     * Clean up a collection if it exists (navigate to it and delete it).
     * Returns on the Collections tab.
     */
    private fun deleteCollectionIfExists(name: String) {
        navigateToCollectionsTab()
        val exists = rule.onAllNodesWithText(name, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (exists) {
            openCollection(name)
            deleteCurrentCollection()
        }
    }

    /**
     * Navigate back from CollectionDetailScreen to the Collections tab.
     * AnimatedContent may not preserve the Library's Collections tab state,
     * so we navigate to the tab explicitly after pressing back.
     */
    private fun backToCollectionsTab() {
        rule.pressBack()
        rule.waitForIdle()
        Thread.sleep(500)

        val hasMyCollections = rule.onAllNodesWithText("My Collections", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (!hasMyCollections) {
            rule.waitForText("Collections", timeout = 5_000)
            rule.tapOn("Collections")
            rule.waitForText("My Collections", timeout = 5_000)
        }
    }

    /**
     * Navigate to Castlevania game detail from wherever we are.
     * Handles being on the Collections tab by going to Home first.
     */
    private fun navigateToGameDetail() {
        rule.tapOn("Home")
        rule.waitForText("Spela", timeout = 5_000)
        rule.navigateToCastlevania()
    }

    // ── Test: Create/Delete basic smoke test ──

    @Test
    fun deleteCollectionFromDetailScreen() {
        rule.startLoggedIn()
        val collName = "E2E Delete ${System.currentTimeMillis()}"

        // Create a fresh collection
        navigateToCollectionsTab()
        createCollection(collName)

        // Open it
        openCollection(collName)

        // Verify we're on the detail screen with owner controls
        rule.waitForContentDescription("Delete collection", timeout = 5_000)
        rule.waitForContentDescription("Edit collection", timeout = 3_000)

        // Delete it
        deleteCurrentCollection()

        // Verify collection is gone from list
        rule.assertTextNotVisible(collName)
    }

    // ── Test: Create validation (empty name shows error) ──

    @Test
    fun createCollectionValidation() {
        rule.startLoggedIn()
        navigateToCollectionsTab()

        // Tap FAB to open create dialog
        rule.onNodeWithContentDescription("Create collection", substring = true).performClick()
        rule.waitForIdle()
        rule.waitForText("Create Collection", timeout = 5_000)

        // Name field should be empty initially — tap Create without entering a name
        rule.onNodeWithText("Create").performClick()

        // Validation error should appear
        rule.waitForText("Name is required", timeout = 3_000)

        // Dialog should still be open — check for a dialog element
        rule.waitForText("Name is required", timeout = 3_000)

        // Cancel the dialog
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForTextNotVisible("Name is required")
    }

    // ── Test: Edit Collection ──

    @Test
    fun editCollectionFromDetailScreen() {
        rule.startLoggedIn()
        val editedName = "E2E Edited ${System.currentTimeMillis()}"

        // Create a collection to edit
        val collName = "E2E Edit ${System.currentTimeMillis()}"
        navigateToCollectionsTab()
        createCollection(collName, description = "Original desc")
        openCollection(collName)

        // Tap Edit button
        rule.onNodeWithContentDescription("Edit collection", substring = true).performClick()
        rule.waitForIdle()
        rule.waitForText("Edit Collection", timeout = 5_000)

        // Clear the name field and type the new name
        rule.onNode(hasText("Name") and hasSetTextAction())
            .performTextClearance()
        rule.onNode(hasText("Name") and hasSetTextAction())
            .performTextInput(editedName)

        // Save
        rule.onNodeWithText("Save").performClick()
        rule.waitForText("Collection updated", timeout = 8_000)

        // Verify the title bar updated
        rule.waitForText(editedName, timeout = 5_000)

        // Navigate back and verify list updated
        backToCollectionsTab()
        rule.waitForText(editedName, timeout = 8_000)

        // Clean up
        openCollection(editedName)
        deleteCurrentCollection()
    }

    // ── Test: Create Collection ──

    @Test
    fun createCollectionFromCollectionsScreen() {
        rule.startLoggedIn()
        val collName = "E2E Create ${System.currentTimeMillis()}"

        // Navigate to Collections tab
        navigateToCollectionsTab()

        // Tap FAB to create
        createCollection(collName, description = "Test description")

        // Verify collection appears in the list
        rule.assertTextVisible(collName)

        // Clean up
        openCollection(collName)
        deleteCurrentCollection()
    }

    // ── Test: Add Game to Collection ──

    @Test
    fun addGameToCollectionFromGameDetail() {
        rule.startLoggedIn()
        val collName = "E2E AddGame ${System.currentTimeMillis()}"

        // Create collection
        navigateToCollectionsTab()
        createCollection(collName)

        // Navigate to Castlevania game detail (go via Home to reset tab state)
        navigateToGameDetail()

        // Tap "Add to collection" button (contentDescription, text is empty)
        rule.onNodeWithContentDescription("Add to collection", substring = true).performClick()
        rule.waitForIdle()
        rule.waitForText("Add to Collection", timeout = 5_000)

        // Select our test collection from the picker
        rule.tapOn(collName)

        // Verify success snackbar
        rule.waitForText("Added to $collName", timeout = 8_000)

        // Navigate to the collection and verify Castlevania is in it
        rule.tapOn("Home")
        rule.waitForText("Spela", timeout = 5_000)
        navigateToCollectionsTab()
        openCollection(collName)

        // Verify Castlevania appears in the collection
        rule.waitForText("Castlevania", timeout = 8_000)

        // Clean up — delete the collection (also removes the game)
        deleteCurrentCollection()
    }

    // ── Test: Remove Game from Collection ──

    @Test
    fun removeGameFromCollectionDetail() {
        rule.startLoggedIn()
        val collName = "E2E RemoveGame ${System.currentTimeMillis()}"

        // Create collection
        navigateToCollectionsTab()
        createCollection(collName)

        // Add Castlevania to the collection
        navigateToGameDetail()
        rule.onNodeWithContentDescription("Add to collection", substring = true).performClick()
        rule.waitForIdle()
        rule.waitForText("Add to Collection", timeout = 5_000)
        rule.tapOn(collName)
        rule.waitForText("Added to $collName", timeout = 8_000)

        // Navigate to the collection
        rule.tapOn("Home")
        rule.waitForText("Spela", timeout = 5_000)
        navigateToCollectionsTab()
        openCollection(collName)

        // Verify Castlevania is in the collection
        rule.waitForText("Castlevania", timeout = 8_000)

        // Remove Castlevania from the collection
        rule.onNodeWithContentDescription("Remove Castlevania from collection", substring = true).performClick()
        rule.waitForIdle()
        rule.waitForText("Removed from collection", timeout = 8_000)

        // Verify Castlevania is gone (collection should be empty now)
        rule.waitForTextNotVisible("Castlevania", timeout = 5_000)

        // Clean up — delete the collection
        deleteCurrentCollection()
    }

    // ── Test: Public toggle shows badge ──

    @Test
    fun publicTogglePersists() {
        rule.startLoggedIn()
        val collName = "E2E Public ${System.currentTimeMillis()}"

        // Create a public collection
        navigateToCollectionsTab()
        createCollection(collName, description = "A public collection", isPublic = true)

        // Open the collection detail
        openCollection(collName)

        // Verify "Public" badge is visible
        rule.waitForText("Public", timeout = 5_000)

        // Clean up
        deleteCurrentCollection()
    }

    // ── User switching helper ──

    /**
     * Sign out the current user, restart the app (to reset LoginViewModel.isLoggedIn
     * which otherwise auto-redirects past the login screen), and log in as a different user.
     */
    private fun switchUser(username: String, password: String) {
        // Navigate to Home first
        rule.tapOn("Home")
        rule.waitForText("Spela", timeout = 5_000)

        // Navigate to Settings and sign out
        rule.navigateToSettings()
        rule.onNodeWithText("Sign Out").performClick()
        rule.waitForText("re-enter your credentials", timeout = 5_000)
        val nodes = rule.onAllNodesWithText("Sign Out").fetchSemanticsNodes()
        rule.onAllNodesWithText("Sign Out")[nodes.size - 1].performClick()
        rule.waitForText("Connect to your game server", timeout = 15_000)

        // Restart app to reset LoginViewModel state (isLoggedIn stays true after
        // logout, which causes LoginScreen to auto-redirect to Home via
        // LaunchedEffect(state.isLoggedIn) before we can enter new credentials).
        rule.restartApp()

        // Login as the new user
        rule.ensureLoggedIn(username, password)
    }

    // ── Test: Ownership hides edit/delete for non-owned collections ──

    @Test
    fun collectionOwnershipHidesEditDelete() {
        // Start as player (default user)
        rule.startLoggedIn()
        val collName = "E2E Ownership ${System.currentTimeMillis()}"

        // Create a public collection as player
        navigateToCollectionsTab()
        createCollection(collName, description = "Player's public collection", isPublic = true)

        // Switch to admin user
        switchUser("admin", "admin123")

        // Navigate to Collections > Public tab to find player's collection
        navigateToCollectionsTab()
        rule.tapOn("Public")
        rule.waitForText(collName, timeout = 8_000)

        // Open the public collection
        openCollection(collName)

        // Verify Edit and Delete buttons are NOT visible (admin doesn't own it)
        rule.assertContentDescriptionNotVisible("Edit collection")
        rule.assertContentDescriptionNotVisible("Delete collection")

        // Clean up: switch back to player and delete the collection
        switchUser("player", "player123")
        navigateToCollectionsTab()
        openCollection(collName)
        deleteCurrentCollection()
    }
}
