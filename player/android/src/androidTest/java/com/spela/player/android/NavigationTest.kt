package com.spela.player.android

import org.junit.Test

@RequiresPhysicalDevice(
    reason = "Drives navigation through Home/Console/Game-Detail UI; depends on " +
        "BaseE2ETest.ensureLoggedIn() which the AVD's AndroidView'd EditText flow " +
        "doesn't reliably authenticate (#1146 root cause)."
)
class NavigationTest : BaseE2ETest() {

    @Test
    fun backStackNavigation() {
        // Drive Home → Consoles → NES → game detail via the canonical
        // helper. The local seed has Castlevania; the CI runner only
        // ships nestest. `navigateToAnyNesGame` finds whichever NES
        // game is available — back-stack navigation doesn't care
        // which.
        // navigateToAnyNesGame already confirms game detail rendered — it
        // returns only after the play/download action-button testTag appears.
        // (The earlier text poll for "Play"/"Download"/"Resume" here was stale:
        // an in-library, unplayed game's primary button reads "New game" /
        // "Continue", so none of those substrings matched.)
        rule.navigateToAnyNesGame()

        // BACK #1: Game Detail → previous screen (Browse / game list).
        rule.pressBack()

        // Verify NES console name is visible (title bar / previous screen).
        rule.waitForText("Nintendo Entertainment System", timeout = 8_000)

        // BACK: keep going until we reach the Consoles grid.
        rule.pressBack()
        rule.waitForContentDescription("Nintendo Entertainment System", timeout = 8_000)
    }

}
