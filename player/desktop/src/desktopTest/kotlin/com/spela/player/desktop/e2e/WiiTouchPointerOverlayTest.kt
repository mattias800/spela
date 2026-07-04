package com.spela.player.desktop.e2e

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.feature.ingame.WiiTouchPointerOverlay
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class WiiTouchPointerOverlayTest {

    @Test
    fun quickTapPressesAWithoutMovingPointer() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                WiiTouchPointerOverlay(
                    controller = harness.libretroController,
                    aspectRatio = 4f / 3f,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("wii-pointer"),
                )
            }
        }
        waitForIdle()

        onNodeWithTag("wii-pointer").performTouchInput {
            click(center)
        }
        mainClock.advanceTimeBy(100)
        waitForIdle()

        assertEquals(
            listOf(
                FakeLibretroController.ButtonEvent(0, LibretroButtons.A, true),
                FakeLibretroController.ButtonEvent(0, LibretroButtons.A, false),
            ),
            harness.libretroController.buttonEvents,
        )
        assertTrue(
            harness.libretroController.pointerEvents.isEmpty(),
            "A tap should not move the held Wii pointer position",
        )
    }

    @Test
    fun rapidTapsEmitDistinctAButtonPulses() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                WiiTouchPointerOverlay(
                    controller = harness.libretroController,
                    aspectRatio = 4f / 3f,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("wii-pointer"),
                )
            }
        }
        waitForIdle()

        onNodeWithTag("wii-pointer").performTouchInput {
            click(center)
        }
        onNodeWithTag("wii-pointer").performTouchInput {
            click(center)
        }
        mainClock.advanceTimeBy(200)
        waitForIdle()

        assertEquals(
            listOf(
                FakeLibretroController.ButtonEvent(0, LibretroButtons.A, true),
                FakeLibretroController.ButtonEvent(0, LibretroButtons.A, false),
                FakeLibretroController.ButtonEvent(0, LibretroButtons.A, true),
                FakeLibretroController.ButtonEvent(0, LibretroButtons.A, false),
            ),
            harness.libretroController.buttonEvents,
        )
        assertTrue(
            harness.libretroController.pointerEvents.isEmpty(),
            "Rapid taps should still avoid moving the held Wii pointer position",
        )
    }

    @Test
    fun dragMovesPointerWithoutPressingA() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                WiiTouchPointerOverlay(
                    controller = harness.libretroController,
                    aspectRatio = 4f / 3f,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("wii-pointer"),
                )
            }
        }
        waitForIdle()

        onNodeWithTag("wii-pointer").performTouchInput {
            down(Offset(200f, 150f))
            moveTo(Offset(240f, 150f))
            up()
        }
        waitForIdle()

        assertTrue(
            harness.libretroController.pointerEvents.any { it.pressed },
            "A drag should press and move the Wii pointer",
        )
        assertFalse(
            harness.libretroController.pointerEvents.last().pressed,
            "Pointer release should clear only the pressed bit",
        )
        assertTrue(
            harness.libretroController.pointerEvents.first().x > 0,
            "The first drag update should map rightward movement to positive pointer X",
        )
        assertTrue(
            harness.libretroController.buttonEvents.isEmpty(),
            "A drag should not emit a tap-to-A button pulse",
        )
    }

    @Test
    fun secondFingerCannotTakeOverActivePointerDrag() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            Box(Modifier.size(400.dp, 300.dp)) {
                WiiTouchPointerOverlay(
                    controller = harness.libretroController,
                    aspectRatio = 4f / 3f,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("wii-pointer"),
                )
            }
        }
        waitForIdle()

        onNodeWithTag("wii-pointer").performTouchInput {
            down(0, Offset(200f, 150f))
            moveTo(0, Offset(240f, 150f))
            down(1, Offset(80f, 80f))
            up(0)
            up(1)
        }
        waitForIdle()

        assertTrue(
            harness.libretroController.pointerEvents.any { it.pressed },
            "The original finger should start pointer aim",
        )
        assertFalse(
            harness.libretroController.pointerEvents.last().pressed,
            "Lifting the original finger should release the pointer even if another finger is down",
        )
        assertTrue(
            harness.libretroController.pointerEvents.filter { it.pressed }.all { it.x >= 0 },
            "The second finger should not take over and move the pointer leftward",
        )
        assertTrue(
            harness.libretroController.buttonEvents.isEmpty(),
            "A multi-touch drag should not emit tap-to-A",
        )
    }
}
