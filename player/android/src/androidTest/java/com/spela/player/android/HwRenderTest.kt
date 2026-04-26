package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * E2E tests for HW-rendered N64 cores on Android. The libretro-NES path is
 * exercised by `EmulationTest` which uses the same nestopia core via the
 * shared software render pipeline, so we no longer mirror those assertions
 * here.
 *
 * Uses Nintendo 64 (Banjo-Kazooie) via the mupen64plus_next core. Verifies
 * that N64 games launch, run, and exercise the emulation lifecycle (overlay,
 * save/load, exit) on a real GLideN64 GLES context.
 *
 * IMPORTANT: N64 tests are combined into fewer methods to minimize core
 * load/unload cycles. The mupen64plus_next Angrylion renderer leaks native
 * threads across dlclose/dlopen, causing instability after ~4-5 sessions.
 * Alphabetical ordering ensures the multi-session test runs first (fresh state).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class HwRenderTest : BaseE2ETest() {

    // N64 core shutdown is slower than NES: auto-save serialization +
    // emulation thread join + native deinit. Angrylion gets progressively
    // slower across sessions due to leaked threads.
    private val n64CoreIdleTimeout = 60_000L

    // ── N64 gameplay tests ──

    private fun setupN64Game() {
        rule.navigateToN64GameAndPlay()
    }

    private fun exitN64Game() {
        rule.exitGame(n64CoreIdleTimeout)
    }

    /**
     * Tests exit-and-resume lifecycle with two N64 sessions.
     * Verifies: exit returns to game detail, second session starts,
     * overlay shows correct controls after resume.
     *
     * Closed via #736 — moving retro_unload_game + retro_deinit onto
     * the emulation thread (matching RetroArch's runloop_event_deinit_core
     * invariant) made the second retro_load_game succeed. The earlier
     * "skip retro_deinit for GL HW cores" workaround that masked this
     * is gone, and mupen64plus_next's "ROM still open" symptom went
     * away with it. Manual repro on AYN Thor verified the fix.
     */
    @Test
    fun n64ExitAndResume() {
        setupN64Game()
        rule.openOverlay()
        exitN64Game()

        // Verify we returned to game detail screen
        rule.waitForVisible("Banjo-Kazooie", timeout = 8_000)

        // Second play session — button may be "Resume" if saves exist
        val hasResume = rule.onAllNodesWithText("Resume", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (hasResume) {
            rule.onNodeWithText("Resume").performClick()
        } else {
            rule.waitForText("Play", timeout = 3_000)
            rule.onNodeWithText("Play").performClick()
        }
        rule.waitForVisible("Game running", timeout = 30_000)

        rule.openOverlay()
        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")

        exitN64Game()
    }

    /**
     * Tests N64 game launch, overlay controls, FPS display, save/load state,
     * and exit — all in a single session to minimize core load/unload cycles.
     */
    @Test
    fun n64GameplayAndSaveLoad() {
        setupN64Game()

        // Verify game is running
        rule.waitForVisible("Game running", timeout = 15_000)

        rule.openOverlay()

        // Verify overlay shows all expected controls
        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")
        rule.assertVisible("Load")
        rule.assertVisible("Fast")
        rule.assertTextVisible("Exit Game")
        rule.assertVisible("Banjo-Kazooie")

        // Save state
        rule.tapOn("Save")
        Thread.sleep(300)
        rule.ensureOverlayOpen()

        // Resume gameplay briefly
        rule.tapOn("Continue")
        rule.waitForTextNotVisible("Exit Game")

        // Reopen overlay and load state
        rule.openOverlay()
        rule.tapOn("Load")
        Thread.sleep(300)
        rule.ensureOverlayOpen()

        // Exit and verify return to game detail
        exitN64Game()
        rule.waitForVisible("Banjo-Kazooie", timeout = 8_000)
        // After exit, button shows "Resume" (save exists) or "Play"
        rule.pollUntil(timeoutMillis = 3_000) {
            try {
                rule.onAllNodesWithText("Play", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Resume", substring = true).fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }
    }

}
