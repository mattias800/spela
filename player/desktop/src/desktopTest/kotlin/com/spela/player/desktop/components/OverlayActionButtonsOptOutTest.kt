package com.spela.player.desktop.components

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.*
import com.spela.player.presentation.ui.feature.ingame.OverlayActionButtons
import kotlin.test.Test

/**
 * UI coverage for the in-game overlay's save-state opt-out gate
 * (#804 phase 4 spec point d). The pure-function gate
 * [com.spela.player.presentation.ui.feature.ingame.shouldShowSaveStateActions]
 * is already covered by SaveStateActionsGateTest in :shared:commonTest;
 * this asserts the rendered effect on actual button visibility.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayActionButtonsOptOutTest {

    @Test
    fun showsSaveLoadChallengeWhenSupportedAndNotOptedOut() = runComposeUiTest {
        setContent {
            Row {
                OverlayActionButtons(
                    isFastForward = false,
                    supportsSaveStates = true,
                    saveStatesOptedOut = false,
                    onSave = {}, onLoad = {}, onScreenshot = {},
                    onToggleFastForward = {}, onChallenge = {}, onControls = {},
                )
            }
        }

        onNodeWithText("Save").assertIsDisplayed()
        onNodeWithText("Load").assertIsDisplayed()
        onNodeWithText("Challenge").assertIsDisplayed()
    }

    @Test
    fun hidesSaveLoadChallengeWhenOptedOut() = runComposeUiTest {
        setContent {
            Row {
                OverlayActionButtons(
                    isFastForward = false,
                    supportsSaveStates = true,
                    saveStatesOptedOut = true,
                    onSave = {}, onLoad = {}, onScreenshot = {},
                    onToggleFastForward = {}, onChallenge = {}, onControls = {},
                )
            }
        }

        onNodeWithText("Save").assertDoesNotExist()
        onNodeWithText("Load").assertDoesNotExist()
        onNodeWithText("Challenge").assertDoesNotExist()
        // Sanity check — non-save actions must still render.
        onNodeWithText("Screenshot").assertIsDisplayed()
        onNodeWithText("Controls").assertIsDisplayed()
    }

    @Test
    fun hidesSaveLoadChallengeWhenCoreDoesNotSupportSaveStates() = runComposeUiTest {
        // Belt-and-braces: pre-opt-out behaviour also hides the row when
        // the core itself can't serialise (e.g. ScummVM). Lock this in
        // so the new opt-out flag doesn't accidentally re-enable buttons
        // for unsupported cores.
        setContent {
            Row {
                OverlayActionButtons(
                    isFastForward = false,
                    supportsSaveStates = false,
                    saveStatesOptedOut = false,
                    onSave = {}, onLoad = {}, onScreenshot = {},
                    onToggleFastForward = {}, onChallenge = {}, onControls = {},
                )
            }
        }

        onNodeWithText("Save").assertDoesNotExist()
        onNodeWithText("Screenshot").assertIsDisplayed()
    }
}
