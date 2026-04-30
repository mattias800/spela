package com.spela.player.desktop.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.domain.model.SaveStateChoice
import com.spela.player.presentation.ui.feature.gamedetail.GameSaveStatePolicyToggle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * UI coverage for [GameSaveStatePolicyToggle], the per-game tri-state
 * radio shown on the game-detail options area (Console default /
 * Always enabled / Always disabled). VM-side wiring is covered by
 * GameDetailViewModelTest; this locks in the rendering contract and
 * the choice each radio dispatches.
 */
@OptIn(ExperimentalTestApi::class)
class GameSaveStatePolicyToggleTest {

    @Test
    fun rendersAllThreeOptions() = runComposeUiTest {
        setContent {
            GameSaveStatePolicyToggle(
                current = SaveStateChoice.Disabled,
                onChange = {},
            )
        }

        onNodeWithTag("game-save-state-policy-toggle").assertIsDisplayed()
        onNodeWithTag("game-save-state-default").assertIsDisplayed()
        onNodeWithTag("game-save-state-enabled").assertIsDisplayed()
        onNodeWithTag("game-save-state-disabled").assertIsDisplayed()
    }

    @Test
    fun tappingConsoleDefaultDispatchesNullChoice() = runComposeUiTest {
        var captured: SaveStateChoice? = SaveStateChoice.Disabled
        var captureCount = 0
        setContent {
            GameSaveStatePolicyToggle(
                current = SaveStateChoice.Disabled,
                onChange = { captured = it; captureCount++ },
            )
        }

        onNodeWithTag("game-save-state-default").performClick()

        assertNull(captured, "tapping 'Console default' clears the override")
        assertEquals(1, captureCount)
    }

    @Test
    fun tappingAlwaysEnabledDispatchesEnabledChoice() = runComposeUiTest {
        var captured: SaveStateChoice? = null
        setContent {
            GameSaveStatePolicyToggle(
                current = null,
                onChange = { captured = it },
            )
        }

        onNodeWithTag("game-save-state-enabled").performClick()

        assertEquals(SaveStateChoice.Enabled, captured)
    }

    @Test
    fun tappingAlwaysDisabledDispatchesDisabledChoice() = runComposeUiTest {
        var captured: SaveStateChoice? = null
        setContent {
            GameSaveStatePolicyToggle(
                current = null,
                onChange = { captured = it },
            )
        }

        onNodeWithTag("game-save-state-disabled").performClick()

        assertEquals(SaveStateChoice.Disabled, captured)
    }
}
