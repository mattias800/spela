package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.screen.SettingsCategory
import com.spela.player.presentation.ui.screen.SettingsCategoryList
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Covers the desktop in-app Quit (#1439): a "Quit Spela" action pinned at the
 * bottom of the Settings category list, shown only when a quit handler is
 * provided (desktop; null on Android → hidden), and a confirm prompt that's
 * navigable with a gamepad/keyboard (focus anchored on Cancel).
 */
@OptIn(ExperimentalTestApi::class)
class SettingsQuitTest {

    private fun ComposeUiTest.renderList(onQuit: (() -> Unit)?) {
        setContent {
            SettingsCategoryList(
                selectedCategory = SettingsCategory.GENERAL,
                onSelectCategory = {},
                username = "tester",
                serverUrl = "http://localhost:8080",
                onQuit = onQuit,
            )
        }
    }

    @Test
    fun quitShownAndInvokesHandlerWhenProvided() = runComposeUiTest {
        var quit = false
        renderList(onQuit = { quit = true })

        // Scroll the category list to the pinned Quit row, then activate it.
        onNode(hasScrollAction()).performScrollToNode(hasTestTag("settings_quit"))
        onNodeWithTag("settings_quit").assertExists()
        onNodeWithTag("settings_quit").performClick()

        assertTrue(quit, "tapping Quit Spela must invoke the quit handler")
    }

    @Test
    fun quitHiddenWhenNoHandler() = runComposeUiTest {
        renderList(onQuit = null)
        // No handler (e.g. Android) → the action must not exist at all.
        onNodeWithTag("settings_quit").assertDoesNotExist()
    }

    @Test
    fun confirmDialogAnchorsFocusOnCancelForGamepad() = runComposeUiTest {
        setContent {
            SpConfirmDialog(
                title = "Quit Spela?",
                message = "Any running game will be saved and closed.",
                onDismiss = {},
                onConfirm = {},
                confirmText = "Quit",
                isDestructive = true,
            )
        }

        // Past the ~120ms focus-settle. The confirm prompt must open with the
        // safe (Cancel) button focused so it's operable by d-pad/keyboard with
        // no mouse — the Steam Deck Gaming Mode requirement (#1439).
        mainClock.advanceTimeBy(300)
        waitForIdle()

        onNodeWithTag("dialog_dismiss").assertIsFocused()
    }
}
