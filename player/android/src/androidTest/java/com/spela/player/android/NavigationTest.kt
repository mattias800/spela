package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Test

class NavigationTest : BaseE2ETest() {

    @Test
    fun backStackNavigation() {
        // Drive Home → Consoles → NES → game detail via the canonical
        // helper. The local seed has Castlevania; the CI runner only
        // ships nestest. `navigateToAnyNesGame` finds whichever NES
        // game is available — back-stack navigation doesn't care
        // which.
        rule.navigateToAnyNesGame()

        // Verify game detail rendered — wait for an action button.
        rule.pollUntil(timeoutMillis = 15_000) {
            try {
                rule.onAllNodesWithText("Download", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Play", substring = true)
                        .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Resume", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }

        // BACK #1: Game Detail → previous screen (Browse / game list).
        rule.pressBack()

        // Verify NES console name is visible (title bar / previous screen).
        rule.waitForText("Nintendo Entertainment System", timeout = 8_000)

        // BACK: keep going until we reach the Consoles grid.
        rule.pressBack()
        rule.waitForContentDescription("Nintendo Entertainment System", timeout = 8_000)
    }

}
