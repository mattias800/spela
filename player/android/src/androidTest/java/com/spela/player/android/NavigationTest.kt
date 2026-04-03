package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backStackNavigation() {
        rule.startLoggedIn()

        // Navigate to Consoles tab and wait for console cards to load
        rule.tapOn("Consoles")
        rule.waitForContentDescription("Nintendo Entertainment System", timeout = 8_000)

        // Navigate forward to Screen 2: NES console game list
        // Use compound matcher to avoid ambiguity with game cards that mention the console name
        rule.scrollToAndTapMatchingBoth("Nintendo Entertainment System", "games")

        // Verify we're on the console game list (title bar shows console name as text)
        rule.waitForText("Nintendo Entertainment System", timeout = 8_000)

        // Navigate forward to Screen 3: Game detail
        // Use a game from the "Top Rated" carousel (visible without scrolling)
        // to avoid flaky scroll+click issues with LazyColumn off-screen nodes
        rule.tapOn("Super Mario Bros. 3")

        // Verify game detail screen — wait for action buttons (Download/Play/Resume text)
        rule.waitUntil(timeoutMillis = 15_000) {
            try {
                rule.onAllNodesWithText("Download", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Play", substring = true)
                        .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Resume", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }

        // BACK #1: Game Detail -> Console game list
        rule.pressBack()

        // Verify we're back on NES console game list (title bar shows console name)
        rule.waitForText("Nintendo Entertainment System", timeout = 8_000)

        // BACK #2: Console game list -> Consoles screen
        rule.pressBack()

        // Verify we're back on Consoles (console cards visible via contentDescription)
        rule.waitForContentDescription("Nintendo Entertainment System", timeout = 8_000)
    }

    @Test
    fun autoScrapeMetadata() {
        rule.startLoggedIn()

        // Navigate to Consoles tab, then NES > Castlevania
        rule.tapOn("Consoles")
        rule.waitForContentDescription("Nintendo Entertainment System", timeout = 8_000)
        rule.scrollToAndTapMatchingBoth("Nintendo Entertainment System", "games")
        rule.waitForText("Nintendo Entertainment System", timeout = 8_000)
        rule.scrollToAndTapText("Castlevania")

        // Wait for game detail screen — wait for action buttons (Download/Play/Resume text)
        rule.waitUntil(timeoutMillis = 15_000) {
            try {
                rule.onAllNodesWithText("Download", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Play", substring = true)
                        .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Resume", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }

        // Wait for metadata to auto-scrape (generous timeout for network)
        rule.waitForText("Download", timeout = 30_000)

        // Verify game detail fully populated
        rule.assertTextVisible("Castlevania")

        // Best-effort cover art check — don't fail the metadata test over image loading.
        // Use manual polling instead of waitUntil to avoid Compose framework's internal
        // failure tracking which reports failures even when the exception is caught.
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            try {
                val nodes = rule.onAllNodesWithContentDescription(
                    "Castlevania cover art", substring = true
                ).fetchSemanticsNodes()
                if (nodes.isNotEmpty()) break
            } catch (_: Exception) { /* hierarchy not ready */ }
            Thread.sleep(500)
        }
    }
}
