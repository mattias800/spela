package com.spela.player.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for the Linux undecorated-window chrome (LinuxTitleBar.kt).
 * Window operations are injected as callbacks, so the chrome is exercised
 * without a real undecorated window; Main.kt wires the callbacks to
 * WindowState / exitApplication.
 */
@OptIn(ExperimentalTestApi::class)
class LinuxTitleBarTest {

    @Test
    fun rendersDragRegionAndAllCaptionButtons() = runComposeUiTest {
        setContent {
            LinuxTitleBarChrome(
                isMaximized = false,
                onMinimize = {},
                onToggleMaximize = {},
                onClose = {},
                onDragStart = { false },
                modifier = Modifier.fillMaxWidth().height(32.dp),
            )
        }

        onNodeWithTag("linux-titlebar").assertIsDisplayed()
        onNodeWithTag("linux-titlebar-drag").assertIsDisplayed()
        onNodeWithTag("linux-titlebar-minimize").assertIsDisplayed()
        onNodeWithTag("linux-titlebar-maximize").assertIsDisplayed()
        onNodeWithTag("linux-titlebar-close").assertIsDisplayed()
    }

    @Test
    fun captionButtonsInvokeTheirCallbacks() = runComposeUiTest {
        var minimized = 0
        var toggled = 0
        var closed = 0
        setContent {
            LinuxTitleBarChrome(
                isMaximized = false,
                onMinimize = { minimized++ },
                onToggleMaximize = { toggled++ },
                onClose = { closed++ },
                onDragStart = { false },
                modifier = Modifier.fillMaxWidth().height(32.dp),
            )
        }

        onNodeWithTag("linux-titlebar-minimize").performClick()
        onNodeWithTag("linux-titlebar-maximize").performClick()
        onNodeWithTag("linux-titlebar-close").performClick()

        assertEquals(1, minimized)
        assertEquals(1, toggled)
        assertEquals(1, closed)
    }

    @Test
    fun doubleClickOnDragRegionTogglesMaximize() = runComposeUiTest {
        var toggled = 0
        setContent {
            LinuxTitleBarChrome(
                isMaximized = false,
                onMinimize = {},
                onToggleMaximize = { toggled++ },
                onClose = {},
                onDragStart = { false },
                modifier = Modifier.fillMaxWidth().height(32.dp),
            )
        }

        onNodeWithTag("linux-titlebar-drag").performTouchInput { doubleClick() }

        assertEquals(1, toggled)
    }

    @Test
    fun dragOnDragRegionStartsWindowMove() = runComposeUiTest {
        var dragStarts = 0
        setContent {
            LinuxTitleBarChrome(
                isMaximized = false,
                onMinimize = {},
                onToggleMaximize = {},
                onClose = {},
                onDragStart = { dragStarts++; true },
                modifier = Modifier.fillMaxWidth().height(32.dp),
            )
        }

        onNodeWithTag("linux-titlebar-drag").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(40f, 0f))
            up()
        }

        assertTrue(dragStarts >= 1, "drag should engage the window move")
    }
}
