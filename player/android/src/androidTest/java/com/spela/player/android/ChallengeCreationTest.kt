package com.spela.player.android

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
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
        rule.navigateToGameAndPlay(preferredGameTitle = "Castlevania")
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

        // All form interaction goes through UiAutomator: Compose's
        // performTextInput / performClick block on Espresso idle
        // during the 60fps emulation render loop.
        val device = androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        )
        val deadline = System.currentTimeMillis() + 5_000L
        var titleField = device.findObject(
            androidx.test.uiautomator.UiSelector().className("android.widget.EditText").instance(0)
        )
        while (!titleField.exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
            titleField = device.findObject(
                androidx.test.uiautomator.UiSelector().className("android.widget.EditText").instance(0)
            )
        }
        check(titleField.exists()) { "Title field not found" }
        titleField.clearTextField()
        titleField.setText("E2E Test Challenge")

        device.findObject(androidx.test.uiautomator.UiSelector().text("Create")).click()

        // US-1 AC: toast "Challenge created!", game resumes.
        rule.waitForText("Challenge created!", timeout = 8_000)

        // Toast auto-dismisses 2s after success; afterwards the
        // panel + overlay are gone and the game is running.
        rule.waitForTextNotVisible("Create Challenge", timeout = 5_000)
        rule.waitForTextNotVisible("Exit Game", timeout = 3_000)

        // Game still running
        rule.waitForVisible("Game running", timeout = 10_000)

        rule.openOverlayAndExit()
    }

    // ── US-1 AC: Challenge type and difficulty can be changed ──

    @Test
    fun createChallengeWithCustomTypeAndDifficulty() {
        setupGame()
        rule.openOverlay()

        rule.tapOn("Challenge")
        rule.waitForText("Create Challenge", timeout = 5_000)

        // Drive the panel via UiAutomator: Compose's performClick /
        // performTextInput block on Espresso idle during the 60fps
        // emulation render loop.
        val device = androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        )
        device.findObject(androidx.test.uiautomator.UiSelector().text("Speedrun")).click()
        device.findObject(androidx.test.uiautomator.UiSelector().text("Hard")).click()

        // Fill title — first EditText
        val titleField = device.findObject(
            androidx.test.uiautomator.UiSelector().className("android.widget.EditText").instance(0)
        )
        check(titleField.exists()) { "Title field not found" }
        titleField.clearTextField()
        titleField.setText("Hard Speedrun Challenge")

        // Submit
        device.findObject(androidx.test.uiautomator.UiSelector().text("Create")).click()
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

        // All form interaction goes through UiAutomator: Compose's
        // performTextInput / performClick block on Espresso idle
        // during the 60fps emulation render loop.
        val device = androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        )

        // Fill title — first EditText on the panel. Poll briefly so
        // we don't race the Compose accessibility tree.
        val deadline = System.currentTimeMillis() + 5_000L
        var titleField = device.findObject(
            androidx.test.uiautomator.UiSelector().className("android.widget.EditText").instance(0)
        )
        while (!titleField.exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
            titleField = device.findObject(
                androidx.test.uiautomator.UiSelector().className("android.widget.EditText").instance(0)
            )
        }
        check(titleField.exists()) { "Title field not found" }
        titleField.clearTextField()
        titleField.setText("Described Challenge")

        // Fill optional description — second EditText.
        val descField = device.findObject(
            androidx.test.uiautomator.UiSelector().className("android.widget.EditText").instance(1)
        )
        check(descField.exists()) { "Description field not found" }
        descField.setText("Beat the first boss without taking damage")

        // Click Create.
        device.findObject(androidx.test.uiautomator.UiSelector().text("Create")).click()
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

        // Tap Cancel via the panel's stable testTag. The tag-based
        // helper invokes the OnClick semantic action directly, which
        // bypasses both Espresso idle (blocked by the 60fps render
        // loop) and the touch-dispatch ambiguity of clicking a Text
        // child of SpSecondaryButton.
        rule.tapOnTag("challenge_create_cancel_button")

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

        // Use the shared helper — it does the overlay open, panel
        // wait, EditText polling and UiAutomator-based Create click
        // (Compose's tapOn blocks on Espresso idle during the 60fps
        // emulation render loop).
        rule.createChallengeFromOverlay("My Created Challenge")

        // Exit game to go back to game detail. The CTA depends on
        // download + auto-save state — after playing once the
        // primary action is "Resume" or "Play", not "Download".
        rule.openOverlayAndExit()
        rule.pollUntil(timeoutMillis = 8_000L) {
            try {
                rule.onAllNodesWithText("Resume", substring = false)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Play", substring = false)
                        .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Download", substring = false)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: Exception) { false }
        }

        // Navigate to challenge list via "View Challenges" button
        rule.navigateToChallengeList()

        // The challenge we created should appear in the list
        rule.waitForText("My Created Challenge", timeout = 8_000)

        rule.pressBack()
    }
}
