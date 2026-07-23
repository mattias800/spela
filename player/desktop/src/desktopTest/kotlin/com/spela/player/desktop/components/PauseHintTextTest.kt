package com.spela.player.desktop.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.libretro.PauseHintText
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import kotlin.test.Test

/**
 * The in-game pause hint must name an input the player actually has (#1682).
 * A Big Picture / Steam Deck player has no keyboard, so "Press Esc" made the
 * overlay — and therefore saving and exiting — undiscoverable for them.
 */
@OptIn(ExperimentalTestApi::class)
class PauseHintTextTest {

    @Test
    fun namesTheGamepadComboInGamepadMode() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalInputMode provides InputMode.GAMEPAD) {
                PauseHintText()
            }
        }

        onNodeWithText("Hold Select + Start to pause").assertIsDisplayed()
    }

    @Test
    fun namesTheEscapeKeyInTouchOrKeyboardMode() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalInputMode provides InputMode.TOUCH) {
                PauseHintText()
            }
        }

        onNodeWithText("Press Esc to pause").assertIsDisplayed()
    }
}
