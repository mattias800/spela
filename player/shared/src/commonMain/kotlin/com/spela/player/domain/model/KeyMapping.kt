package com.spela.player.domain.model

import com.spela.player.presentation.viewmodel.LibretroAnalog
import com.spela.player.presentation.viewmodel.LibretroButtons

/**
 * A single binding between a platform key code and a libretro button.
 */
data class KeyBinding(
    val platformKeyCode: Int,
    val retroButtonId: Int,
)

/**
 * A complete set of bindings for a specific console and controller port.
 */
data class KeyMappingProfile(
    val consoleId: String,
    val port: Int,
    val bindings: Map<Int, Int>, // retroButtonId -> platformKeyCode
)

/**
 * Describes which libretro buttons a specific console uses,
 * along with a human-readable label for each button.
 * Used by the UI to render the correct buttons for each console.
 */
data class ConsoleButtonLayout(
    val consoleId: String,
    val displayName: String,
    val buttons: List<ButtonInfo>,
)

/**
 * Metadata for a single button in a console's layout.
 */
data class ButtonInfo(
    val retroButtonId: Int,
    val label: String,
)

/** Well-known console ID used for the global default mapping. */
const val DEFAULT_CONSOLE_ID = "__default__"

/**
 * Provides the button layouts for all supported consoles and the default key mappings.
 */
object DefaultKeyMappings {

    private val dpadButtons = listOf(
        ButtonInfo(LibretroButtons.UP, "Up"),
        ButtonInfo(LibretroButtons.DOWN, "Down"),
        ButtonInfo(LibretroButtons.LEFT, "Left"),
        ButtonInfo(LibretroButtons.RIGHT, "Right"),
    )

    private val leftStickButtons = listOf(
        ButtonInfo(LibretroAnalog.LEFT_STICK_UP, "L-Stick Up"),
        ButtonInfo(LibretroAnalog.LEFT_STICK_DOWN, "L-Stick Down"),
        ButtonInfo(LibretroAnalog.LEFT_STICK_LEFT, "L-Stick Left"),
        ButtonInfo(LibretroAnalog.LEFT_STICK_RIGHT, "L-Stick Right"),
    )

    private val rightStickButtons = listOf(
        ButtonInfo(LibretroAnalog.RIGHT_STICK_UP, "R-Stick Up"),
        ButtonInfo(LibretroAnalog.RIGHT_STICK_DOWN, "R-Stick Down"),
        ButtonInfo(LibretroAnalog.RIGHT_STICK_LEFT, "R-Stick Left"),
        ButtonInfo(LibretroAnalog.RIGHT_STICK_RIGHT, "R-Stick Right"),
    )

    val NES = ConsoleButtonLayout(
        consoleId = "nes",
        displayName = "NES",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.START, "Start"),
            ButtonInfo(LibretroButtons.SELECT, "Select"),
        ),
    )

    val SNES = ConsoleButtonLayout(
        consoleId = "snes",
        displayName = "SNES",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.X, "X"),
            ButtonInfo(LibretroButtons.Y, "Y"),
            ButtonInfo(LibretroButtons.L, "L"),
            ButtonInfo(LibretroButtons.R, "R"),
            ButtonInfo(LibretroButtons.START, "Start"),
            ButtonInfo(LibretroButtons.SELECT, "Select"),
        ),
    )

    val N64 = ConsoleButtonLayout(
        consoleId = "n64",
        displayName = "N64",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.START, "Start"),
            ButtonInfo(LibretroButtons.L, "L"),
            ButtonInfo(LibretroButtons.R, "R"),
            ButtonInfo(LibretroButtons.L2, "Z"),
            // C-buttons mapped to right analog or face buttons
            ButtonInfo(LibretroButtons.X, "C-Left"),
            ButtonInfo(LibretroButtons.Y, "C-Down"),
            ButtonInfo(LibretroButtons.L3, "C-Up"),
            ButtonInfo(LibretroButtons.R3, "C-Right"),
        ) + leftStickButtons,
    )

    val GENESIS = ConsoleButtonLayout(
        consoleId = "genesis",
        displayName = "Genesis / Mega Drive",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.Y, "C"),
            ButtonInfo(LibretroButtons.START, "Start"),
            // 6-button mode
            ButtonInfo(LibretroButtons.X, "X"),
            ButtonInfo(LibretroButtons.L, "Y"),
            ButtonInfo(LibretroButtons.R, "Z"),
        ),
    )

    val GAMEBOY = ConsoleButtonLayout(
        consoleId = "gb",
        displayName = "Game Boy",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.START, "Start"),
            ButtonInfo(LibretroButtons.SELECT, "Select"),
        ),
    )

    val GBA = ConsoleButtonLayout(
        consoleId = "gba",
        displayName = "Game Boy Advance",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.L, "L"),
            ButtonInfo(LibretroButtons.R, "R"),
            ButtonInfo(LibretroButtons.START, "Start"),
            ButtonInfo(LibretroButtons.SELECT, "Select"),
        ),
    )

    val PSX = ConsoleButtonLayout(
        consoleId = "psx",
        displayName = "PlayStation",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.B, "Cross"),
            ButtonInfo(LibretroButtons.A, "Circle"),
            ButtonInfo(LibretroButtons.Y, "Square"),
            ButtonInfo(LibretroButtons.X, "Triangle"),
            ButtonInfo(LibretroButtons.L, "L1"),
            ButtonInfo(LibretroButtons.R, "R1"),
            ButtonInfo(LibretroButtons.L2, "L2"),
            ButtonInfo(LibretroButtons.R2, "R2"),
            ButtonInfo(LibretroButtons.SELECT, "Select"),
            ButtonInfo(LibretroButtons.START, "Start"),
            ButtonInfo(LibretroButtons.L3, "L3"),
            ButtonInfo(LibretroButtons.R3, "R3"),
        ) + leftStickButtons + rightStickButtons,
    )

    val PSP = ConsoleButtonLayout(
        consoleId = "psp",
        displayName = "PSP",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.B, "Cross"),
            ButtonInfo(LibretroButtons.A, "Circle"),
            ButtonInfo(LibretroButtons.Y, "Square"),
            ButtonInfo(LibretroButtons.X, "Triangle"),
            ButtonInfo(LibretroButtons.L, "L"),
            ButtonInfo(LibretroButtons.R, "R"),
            ButtonInfo(LibretroButtons.SELECT, "Select"),
            ButtonInfo(LibretroButtons.START, "Start"),
        ) + leftStickButtons,
    )

    val NDS = ConsoleButtonLayout(
        consoleId = "nds",
        displayName = "Nintendo DS",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.X, "X"),
            ButtonInfo(LibretroButtons.Y, "Y"),
            ButtonInfo(LibretroButtons.L, "L"),
            ButtonInfo(LibretroButtons.R, "R"),
            ButtonInfo(LibretroButtons.START, "Start"),
            ButtonInfo(LibretroButtons.SELECT, "Select"),
        ),
    )

    val THREEDS = ConsoleButtonLayout(
        consoleId = "3ds",
        displayName = "Nintendo 3DS",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.X, "X"),
            ButtonInfo(LibretroButtons.Y, "Y"),
            ButtonInfo(LibretroButtons.L, "L"),
            ButtonInfo(LibretroButtons.R, "R"),
            ButtonInfo(LibretroButtons.START, "Start"),
            ButtonInfo(LibretroButtons.SELECT, "Select"),
        ) + leftStickButtons,
    )

    val DREAMCAST = ConsoleButtonLayout(
        consoleId = "dc",
        displayName = "Dreamcast",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.X, "X"),
            ButtonInfo(LibretroButtons.Y, "Y"),
            ButtonInfo(LibretroButtons.L, "L"),
            ButtonInfo(LibretroButtons.R, "R"),
            ButtonInfo(LibretroButtons.START, "Start"),
        ) + leftStickButtons,
    )

    val GC = ConsoleButtonLayout(
        consoleId = "gc",
        displayName = "GameCube",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.X, "X"),
            ButtonInfo(LibretroButtons.Y, "Y"),
            ButtonInfo(LibretroButtons.L, "L"),
            ButtonInfo(LibretroButtons.R, "R"),
            ButtonInfo(LibretroButtons.R2, "Z"),
            ButtonInfo(LibretroButtons.START, "Start"),
        ) + leftStickButtons + rightStickButtons,
    )

    val SATURN = ConsoleButtonLayout(
        consoleId = "sat",
        displayName = "Saturn",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.Y, "C"),
            ButtonInfo(LibretroButtons.X, "X"),
            ButtonInfo(LibretroButtons.L, "Y"),
            ButtonInfo(LibretroButtons.R, "Z"),
            ButtonInfo(LibretroButtons.L2, "L"),
            ButtonInfo(LibretroButtons.R2, "R"),
            ButtonInfo(LibretroButtons.START, "Start"),
        ) + leftStickButtons,
    )

    /** All known console layouts, indexed by console ID. */
    val allLayouts: Map<String, ConsoleButtonLayout> = listOf(
        NES, SNES, N64, GENESIS, GAMEBOY, GBA, PSX, PSP, NDS,
        THREEDS, DREAMCAST, SATURN, GC,
    ).associateBy { it.consoleId }

    /**
     * The full 16-button layout used as the global default.
     * Covers all libretro joypad buttons.
     */
    val FULL = ConsoleButtonLayout(
        consoleId = DEFAULT_CONSOLE_ID,
        displayName = "Default (All Buttons)",
        buttons = dpadButtons + listOf(
            ButtonInfo(LibretroButtons.A, "A"),
            ButtonInfo(LibretroButtons.B, "B"),
            ButtonInfo(LibretroButtons.X, "X"),
            ButtonInfo(LibretroButtons.Y, "Y"),
            ButtonInfo(LibretroButtons.L, "L"),
            ButtonInfo(LibretroButtons.R, "R"),
            ButtonInfo(LibretroButtons.L2, "L2"),
            ButtonInfo(LibretroButtons.R2, "R2"),
            ButtonInfo(LibretroButtons.L3, "L3"),
            ButtonInfo(LibretroButtons.R3, "R3"),
            ButtonInfo(LibretroButtons.START, "Start"),
            ButtonInfo(LibretroButtons.SELECT, "Select"),
        ) + leftStickButtons + rightStickButtons,
    )

    /**
     * Returns the button layout for a given console ID.
     * Falls back to the full layout if the console is unknown.
     */
    fun getLayoutForConsole(consoleId: String): ConsoleButtonLayout {
        return allLayouts[consoleId.lowercase()] ?: FULL
    }
}
