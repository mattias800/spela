package com.spela.player.desktop.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.spela.player.presentation.ui.components.SpConfirmDialog
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Coverage for SpConfirmDialog / SpDialog. The width cap (#1258) keeps the
 * confirmation modal compact on wide desktop windows instead of taking 85%
 * of the window. The desktop test scene is wide, so without the cap the
 * surface would measure well over 420 dp.
 */
@OptIn(ExperimentalTestApi::class)
class SpConfirmDialogTest {

    @Test
    fun rendersTitleMessageAndConfirm() = runComposeUiTest {
        setContent {
            SpConfirmDialog(
                title = "Delete Download",
                message = "Remove the downloaded game files from this device?",
                onDismiss = {},
                onConfirm = {},
                confirmText = "Delete",
                isDestructive = true,
            )
        }

        onNodeWithText("Delete Download").assertIsDisplayed()
        onNodeWithText("Remove the downloaded game files from this device?").assertIsDisplayed()
        onNodeWithTag("dialog_confirm").assertIsDisplayed()
    }

    @Test
    fun surfaceWidthIsCappedOnWideWindow() = runComposeUiTest {
        setContent {
            SpConfirmDialog(
                title = "Delete Download",
                message = "Remove the downloaded game files from this device?",
                onDismiss = {},
                onConfirm = {},
                confirmText = "Delete",
                isDestructive = true,
            )
        }

        val width = onNodeWithTag("sp_dialog_surface").getUnclippedBoundsInRoot().width
        // Cap is 420.dp (SpDialogMaxWidth); allow a 1.dp tolerance.
        assertTrue(width <= 421.dp, "dialog surface width $width should be capped at ~420.dp")
    }
}
