package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.state.ControlTab
import com.spela.player.presentation.ui.feature.ingame.SecondaryControlsPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the secondary screen controls page:
 * - Segmented tab selector (Gamepad | Keyboard | Trackpad)
 * - P1/P2 port selector within Gamepad tab
 * - Keyboard layer UI
 * - Trackpad click buttons
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SecondaryScreenControlsTest {

    // --- Tab selector tests ---

    @Test
    fun controlsPageShowsTabSelector() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Gamepad input mode").assertExists()
        onNodeWithContentDescription("Keyboard input mode").assertExists()
        onNodeWithContentDescription("Trackpad input mode").assertExists()
        onNodeWithContentDescription("Input mode: gamepad").assertExists()
    }

    @Test
    fun tabSwitchCallsCallback() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        var selectedTab: ControlTab? = null

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = { selectedTab = it },
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Keyboard input mode").performClick()
        waitForIdle()

        assertEquals(ControlTab.KEYBOARD, selectedTab)
    }

    @Test
    fun tabSwitchToTrackpadCallsCallback() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        var selectedTab: ControlTab? = null

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "dos",
                onSelectPort = {},
                onSelectTab = { selectedTab = it },
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Trackpad input mode").performClick()
        waitForIdle()

        assertEquals(ControlTab.TRACKPAD, selectedTab)
    }

    // --- Gamepad tab tests ---

    @Test
    fun gamepadTabShowsPortSelector() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Player 1 controls").assertExists()
        onNodeWithContentDescription("Player 2 controls").assertExists()
        onNodeWithContentDescription("Control port: Player 1").assertExists()
    }

    @Test
    fun portSelectorSwitchesToP2() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 1,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Control port: Player 2").assertExists()
    }

    @Test
    fun portSelectorCallsCallback() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        var selectedPort = -1

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = { selectedPort = it },
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Player 2 controls").performClick()
        waitForIdle()

        assertEquals(1, selectedPort, "Expected selectedPort to be 1 after clicking P2")
    }

    @Test
    fun portSelectorCallsCallbackForP1() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        var selectedPort = -1

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 1,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = { selectedPort = it },
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Player 1 controls").performClick()
        waitForIdle()

        assertEquals(0, selectedPort, "Expected selectedPort to be 0 after clicking P1")
    }

    // --- Keyboard tab tests ---

    @Test
    fun keyboardTabShowsQwertyLayer() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.KEYBOARD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Virtual keyboard, QWERTY layer").assertExists()
        onNodeWithContentDescription("Key q").assertExists()
        onNodeWithContentDescription("Key a").assertExists()
        onNodeWithContentDescription("Key z").assertExists()
    }

    @Test
    fun keyboardTabShowsPlatformLayerForAmiga() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.KEYBOARD,
                consoleId = "amiga",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Amiga layer inactive").assertExists()
    }

    @Test
    fun keyboardTabShowsPlatformLayerForC64() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.KEYBOARD,
                consoleId = "c64",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("C64 layer inactive").assertExists()
    }

    @Test
    fun keyboardTabShowsModifierKeys() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.KEYBOARD,
                consoleId = "dos",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Shift inactive").assertExists()
        onNodeWithContentDescription("Ctrl inactive").assertExists()
        onNodeWithContentDescription("Alt inactive").assertExists()
        onNodeWithContentDescription("Fn layer inactive").assertExists()
        onNodeWithContentDescription("Sym layer inactive").assertExists()
    }

    // --- Trackpad tab tests ---

    @Test
    fun trackpadTabShowsClickButtons() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.TRACKPAD,
                consoleId = "dos",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Trackpad area, drag to move cursor").assertExists()
        onNodeWithContentDescription("Left Click").assertExists()
        onNodeWithContentDescription("Right Click").assertExists()
    }

    // --- Integration tests via full harness ---

    @Test
    fun touchControlPortResetsOnNewGame() = runComposeUiTest {
        val harness = createHarnessWithNesGame()

        harness.emulationViewModel.onIntent(EmulationIntent.StartGame(gameId = "1"))
        mainClock.autoAdvance = false
        repeat(4) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        harness.emulationViewModel.onIntent(EmulationIntent.SelectTouchControlPort(1))
        mainClock.autoAdvance = false
        repeat(2) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        assertEquals(
            1,
            harness.emulationViewModel.state.value.touchControlPort,
            "Expected touchControlPort to be 1 after selecting port 1"
        )

        harness.emulationViewModel.onIntent(EmulationIntent.StopGame)
        mainClock.autoAdvance = false
        repeat(4) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        assertEquals(
            0,
            harness.emulationViewModel.state.value.touchControlPort,
            "Expected touchControlPort to reset to 0 after stopping game"
        )
    }

    // -- Helpers ---------------------------------------------------------------

    private fun createHarnessWithNesGame(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        return harness
    }
}
