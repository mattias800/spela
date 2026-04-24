package com.spela.player.android

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/**
 * E2E tests for challenge creation flow (US-1).
 *
 * The player opens the in-game overlay while playing and taps "Challenge"
 * to create a new challenge from the current save state. A creation form
 * collects title, type, difficulty, and optional description.
 *
 * Prerequisites:
 * - Server running with seeded data (player/player123 user, Castlevania game)
 * - Device connected and unlocked
 */
class ChallengeCreationTest : BaseE2ETest() {

    private fun setupGame() {
        rule.navigateToGameAndPlay()
    }

    // ── US-1 AC: "Challenge" button appears in emulation overlay ──

    @Test
    fun challengeButtonVisibleInOverlay() {
        setupGame()
        rule.openOverlay()

        // "Challenge" button should be present alongside Save, Load, Screenshot, Fast, Controls
        rule.assertVisible("Challenge")

        // Existing controls still present (regression check)
        rule.assertVisible("Save")
        rule.assertVisible("Load")
        rule.assertVisible("Screenshot")
        rule.assertVisible("Fast")
        rule.assertVisible("Controls")

        rule.exitGame()
    }

    // ── US-1 AC: Tapping "Challenge" auto-captures state and opens form ──

    @Test
    fun challengeButtonOpensCreationForm() {
        setupGame()
        rule.openOverlay()

        rule.tapOn("Challenge")
        rule.waitForText("Create Challenge", timeout = 5_000)

        // Form fields visible
        rule.assertVisible("Title")
        rule.assertVisible("Type")
        rule.assertVisible("Difficulty")

        // Default values per spec: Type=Completion, Difficulty=Medium
        // These are chip selectors — all options are always visible, selected one highlighted
        rule.assertTextVisible("Completion")
        rule.assertTextVisible("Speedrun")
        rule.assertTextVisible("Survival")
        rule.assertTextVisible("Easy")
        rule.assertTextVisible("Medium")
        rule.assertTextVisible("Hard")

        // Cancel to return to overlay
        rule.tapOn("Cancel")
        rule.waitForText("Exit Game")
        rule.exitGame()
    }

    // ── US-1 AC: Creation form title is required ──

    @Test
    fun challengeCreationDisabledWithEmptyTitle() {
        setupGame()
        rule.openOverlay()

        rule.tapOn("Challenge")
        rule.waitForText("Create Challenge", timeout = 5_000)

        // Clear the pre-filled title suggestion
        rule.clearTextField("Title")

        // Create button should be disabled when title is empty
        rule.onNodeWithText("Create").assertIsNotEnabled()

        // Form should still be visible (did not navigate away)
        rule.assertTextVisible("Create Challenge")

        rule.tapOn("Cancel")
        rule.waitForText("Exit Game")
        rule.exitGame()
    }

    // ── US-1 AC: Successful challenge creation ──

    @Test
    fun createChallengeSuccessfully() {
        setupGame()
        rule.openOverlay()

        rule.tapOn("Challenge")
        rule.waitForText("Create Challenge", timeout = 5_000)

        // Fill in the form
        rule.clearTextField("Title")
        rule.onNode(hasText("Title") and hasSetTextAction())
            .performTextInput("E2E Test Challenge")

        // Submit
        rule.tapOn("Create")

        // US-1 AC: toast "Challenge created!", game resumes
        rule.waitForText("Challenge created!", timeout = 8_000)

        // Overlay should be dismissed, game running
        rule.assertTextNotVisible("Create Challenge")
        rule.assertTextNotVisible("Exit Game")

        // Game still running
        rule.waitForVisible("Game running", timeout = 5_000)

        rule.openOverlayAndExit()
    }

    // ── US-1 AC: Challenge type and difficulty can be changed ──

    @Test
    fun createChallengeWithCustomTypeAndDifficulty() {
        setupGame()
        rule.openOverlay()

        rule.tapOn("Challenge")
        rule.waitForText("Create Challenge", timeout = 5_000)

        // Type and difficulty chips are always visible — tap the desired one directly
        rule.tapOn("Speedrun")
        rule.tapOn("Hard")

        // Fill title
        rule.clearTextField("Title")
        rule.onNode(hasText("Title") and hasSetTextAction())
            .performTextInput("Hard Speedrun Challenge")

        // Submit
        rule.tapOn("Create")
        rule.waitForText("Challenge created!", timeout = 8_000)

        rule.openOverlayAndExit()
    }

    // ── US-1 AC: Optional description field ──

    @Test
    fun createChallengeWithDescription() {
        setupGame()
        rule.openOverlay()

        rule.tapOn("Challenge")
        rule.waitForText("Create Challenge", timeout = 5_000)

        // Fill title
        rule.clearTextField("Title")
        rule.onNode(hasText("Title") and hasSetTextAction())
            .performTextInput("Described Challenge")

        // Fill optional description — label is "Description (optional)"
        rule.onNode(hasText("Description", substring = true) and hasSetTextAction())
            .performTextInput("Beat the first boss without taking damage")

        rule.tapOn("Create")
        rule.waitForText("Challenge created!", timeout = 8_000)

        rule.openOverlayAndExit()
    }

    // ── Cancel challenge creation returns to overlay ──

    @Test
    fun cancelChallengeCreationReturnsToOverlay() {
        setupGame()
        rule.openOverlay()

        rule.tapOn("Challenge")
        rule.waitForText("Create Challenge", timeout = 5_000)

        // Tap Cancel button to dismiss creation panel
        rule.tapOn("Cancel")

        // Creation panel should be dismissed
        rule.waitForTextNotVisible("Create Challenge")

        // Overlay should still be visible with its controls
        rule.waitForText("Exit Game", timeout = 5_000)
        rule.assertVisible("Save")
        rule.assertVisible("Challenge")

        rule.exitGame()
    }

    // ── US-1 AC: Hidden in netplay mode ──
    // Note: This test requires netplay setup, which is complex for E2E.
    // Covered by desktop tests with FakeLibretroController. Skipped for Android E2E.

    // ── US-1 AC: Gated on supportsSaveStates ──
    // Note: Castlevania (NES/nestopia) supports save states, so the button is always visible
    // in our standard test game. Testing the gating on unsupported cores requires a game
    // without save state support, which isn't in our seed data. Covered by unit tests.

    // ── Created challenge appears in challenge list for the game ──

    @Test
    fun createdChallengeVisibleInChallengeList() {
        setupGame()
        rule.openOverlay()

        rule.tapOn("Challenge")
        rule.waitForText("Create Challenge", timeout = 5_000)

        rule.clearTextField("Title")
        rule.onNode(hasText("Title") and hasSetTextAction())
            .performTextInput("My Created Challenge")

        rule.tapOn("Create")
        rule.waitForText("Challenge created!", timeout = 8_000)

        // Exit game to go back to game detail
        rule.openOverlayAndExit()
        rule.waitForText("Download", timeout = 8_000)

        // Navigate to challenge list via "View Challenges" button
        rule.navigateToChallengeList()

        // The challenge we created should appear in the list
        rule.waitForText("My Created Challenge", timeout = 8_000)

        rule.pressBack()
    }
}
