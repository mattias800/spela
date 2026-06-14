package com.spela.player.libretro

import com.spela.player.domain.model.DEFAULT_CONSOLE_ID
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GamepadMappingMigrationTest {

    // android.view.KeyEvent values.
    private val BUTTON_A = 96 // -> SOUTH
    private val BUTTON_B = 97 // -> EAST
    private val VOLUME_UP = 24 // not a gamepad key

    private fun row(console: String, keyCode: Int, retro: Int) =
        GamepadMappingMigration.LegacyRow(console, 0, keyCode, retro)

    @Test
    fun convertsGenuinePerConsoleCustomization() {
        // NES bottom button customized to RetroPad A (default is B) -> migrate.
        val out = GamepadMappingMigration.split(listOf(row("nes", BUTTON_A, LibretroButtons.A)))
        assertEquals(1, out.size)
        assertEquals(GamepadPosition.SOUTH, out[0].position)
        assertEquals(LibretroButtons.A, out[0].retroButtonId)
        assertEquals("nes", out[0].consoleId)
    }

    @Test
    fun skipsDefaultEqualMappings() {
        // BUTTON_A -> RetroPad B equals the positional default for SOUTH: no override needed.
        assertTrue(GamepadMappingMigration.split(listOf(row("nes", BUTTON_A, LibretroButtons.B))).isEmpty())
        assertTrue(GamepadMappingMigration.split(listOf(row("nes", BUTTON_B, LibretroButtons.A))).isEmpty())
    }

    @Test
    fun skipsGlobalDefaultConsole() {
        // The global default isn't a user binding; the positional default covers it.
        assertTrue(GamepadMappingMigration.split(listOf(row(DEFAULT_CONSOLE_ID, BUTTON_A, LibretroButtons.A))).isEmpty())
    }

    @Test
    fun dropsNonGamepadKeyCodes() {
        // e.g. a desktop keyboard code on the wrong platform normalizes to null.
        assertTrue(GamepadMappingMigration.split(listOf(row("nes", VOLUME_UP, LibretroButtons.A))).isEmpty())
    }

    @Test
    fun isIdempotentOverInput() {
        val rows = listOf(
            row("nes", BUTTON_A, LibretroButtons.A),
            row("snes", BUTTON_B, LibretroButtons.X),
        )
        assertEquals(GamepadMappingMigration.split(rows), GamepadMappingMigration.split(rows))
    }
}
