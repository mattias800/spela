package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.state.SecondaryToastData
import com.spela.player.presentation.state.SecondaryToastType
import com.spela.player.presentation.ui.feature.ingame.SecondaryToast
import kotlin.test.Test

/**
 * E2E tests for the SecondaryToast composable.
 *
 * Tests cover:
 * - Save toast rendering with correct message and icon
 * - Load toast rendering with correct message and icon
 *
 * SecondaryToast is a stateless composable rendered directly with
 * explicit parameters. No ViewModel is needed.
 */
@OptIn(ExperimentalTestApi::class)
class SecondaryScreenToastTest {

    @Test
    fun saveToastShowsCorrectMessage() = runComposeUiTest {
        val toastData = SecondaryToastData(
            message = "Saved to Slot 3",
            type = SecondaryToastType.SAVE,
        )

        mainClock.autoAdvance = false

        setContent {
            SecondaryToast(
                toast = toastData,
            )
        }

        // Advance clock to let the LaunchedEffect trigger and make the toast visible
        mainClock.advanceTimeBy(500)
        waitForIdle()

        // Toast pill should be visible with the correct content description
        onNodeWithContentDescription("Saved to Slot 3 toast")
            .assertExists()
            .assertIsDisplayed()

        // Message text should be visible
        onNodeWithText("Saved to Slot 3")
            .assertExists()
            .assertIsDisplayed()

        // Save icon should be present
        onNodeWithContentDescription("Save")
            .assertExists()
    }

    @Test
    fun loadToastShowsCorrectMessage() = runComposeUiTest {
        val toastData = SecondaryToastData(
            message = "Loaded from Slot 1",
            type = SecondaryToastType.LOAD,
        )

        mainClock.autoAdvance = false

        setContent {
            SecondaryToast(
                toast = toastData,
            )
        }

        // Advance clock to let the LaunchedEffect trigger and make the toast visible
        mainClock.advanceTimeBy(500)
        waitForIdle()

        // Toast pill should be visible with the correct content description
        onNodeWithContentDescription("Loaded from Slot 1 toast")
            .assertExists()
            .assertIsDisplayed()

        // Message text should be visible
        onNodeWithText("Loaded from Slot 1")
            .assertExists()
            .assertIsDisplayed()

        // Load icon should be present
        onNodeWithContentDescription("Load")
            .assertExists()
    }
}
