package com.spela.player.libretro

import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.navigation.NavigationEvent
import com.spela.player.presentation.navigation.NavigationEventBus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GamepadUiNavigatorTest {

    private val keys = mutableListOf<Int>()
    private val bus = NavigationEventBus()
    private var inGame = false
    private val nav = GamepadUiNavigator(
        navigationEventBus = bus,
        isInGame = { inGame },
        synthesizeKey = { keys.add(it) },
    )

    /** A one-frame controller state with the given GamepadPositions pressed.
     *  buttons is indexed by GamepadPosition ordinal (the input layer). */
    private fun pressed(vararg positions: GamepadPosition): Array<GamepadState> {
        val buttons = BooleanArray(16)
        positions.forEach { buttons[it.ordinal] = true }
        return arrayOf(GamepadState(0, "Test Controller", buttons, IntArray(6), 0))
    }

    private val released = arrayOf(GamepadState(0, "Test Controller", BooleanArray(16), IntArray(6), 0))

    @Test
    fun dpadDirectionsMapToArrowKeys() {
        nav.handle(pressed(GamepadPosition.DPAD_UP)); nav.handle(released)
        nav.handle(pressed(GamepadPosition.DPAD_DOWN)); nav.handle(released)
        nav.handle(pressed(GamepadPosition.DPAD_LEFT)); nav.handle(released)
        nav.handle(pressed(GamepadPosition.DPAD_RIGHT)); nav.handle(released)

        assertEquals(
            listOf(KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT),
            keys,
        )
    }

    @Test
    fun confirmMapsToEnterAndBackMapsToEscape() {
        // SOUTH = bottom face button = confirm; EAST = right face = back.
        nav.handle(pressed(GamepadPosition.SOUTH)); nav.handle(released)
        nav.handle(pressed(GamepadPosition.EAST)); nav.handle(released)

        assertEquals(listOf(KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE), keys)
    }

    @Test
    fun confirmDoesNotRepeatWhileHeld() {
        repeat(100) { nav.handle(pressed(GamepadPosition.SOUTH)) }
        assertEquals(listOf(KeyEvent.VK_ENTER), keys)
    }

    @Test
    fun dpadAutoRepeatsAfterInitialDelay() {
        // Tick 0 fires immediately; no repeat through the initial-delay window.
        repeat(45) { nav.handle(pressed(GamepadPosition.DPAD_RIGHT)) }
        assertEquals(1, keys.size, "only the initial press should have fired during the delay")

        // First repeat fires once the hold passes the initial delay.
        nav.handle(pressed(GamepadPosition.DPAD_RIGHT))
        assertEquals(2, keys.size)

        // Then one more repeat per interval.
        repeat(8) { nav.handle(pressed(GamepadPosition.DPAD_RIGHT)) }
        assertEquals(3, keys.size)

        keys.forEach { assertEquals(KeyEvent.VK_RIGHT, it) }
    }

    @Test
    fun releasingResetsAutoRepeat() {
        nav.handle(pressed(GamepadPosition.DPAD_RIGHT)) // fire 1
        nav.handle(released)                            // reset
        nav.handle(pressed(GamepadPosition.DPAD_RIGHT)) // fire 2 (fresh press, not a repeat)
        assertEquals(2, keys.size)
    }

    @Test
    fun noSynthWhileInGame() {
        inGame = true
        repeat(10) { nav.handle(pressed(GamepadPosition.DPAD_RIGHT)) }
        assertTrue(keys.isEmpty())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun shouldersEmitSectionEventsUsingCorrectButtonIndices() = runTest {
        val received = mutableListOf<NavigationEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.collect { received.add(it) }
        }

        // L1 -> previous, R1 -> next. Positional reads (input layer), independent
        // of any per-console GamepadPosition->RetroPad remapping.
        nav.handle(pressed(GamepadPosition.R1)); nav.handle(released)
        nav.handle(pressed(GamepadPosition.L1)); nav.handle(released)
        advanceUntilIdle()

        assertEquals(
            listOf(NavigationEvent.NextSection, NavigationEvent.PreviousSection),
            received,
        )
        job.cancel()
    }
}
