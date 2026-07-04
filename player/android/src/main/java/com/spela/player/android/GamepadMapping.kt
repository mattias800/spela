package com.spela.player.android

import android.view.KeyEvent
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.libretro.GamepadButtonResolver
import com.spela.player.presentation.viewmodel.LibretroButtons

/**
 * Maps Android gamepad key codes to libretro joypad button IDs.
 *
 * Supports both the hardcoded default mapping and custom mappings loaded
 * from [KeyMappingRepository]. Call [loadCustomMapping] to apply a custom
 * mapping; [mapKeyToLibretro] will use it if available, falling back to
 * the hardcoded default.
 *
 * Button layout follows the standard mapping where Android's physical A/B buttons
 * map to libretro's B/A respectively (Nintendo-style layout: right button = A action).
 */
object GamepadMapping {
    data class TriggerRoute(
        val analogPressures: Map<Int, Short>,
        val digitalPressed: Map<Int, Boolean>,
    )


    /**
     * The hardcoded default mapping: retroButtonId -> Android keyCode.
     * Used as the platform default when no custom mapping is configured.
     */
    val hardcodedDefaults: Map<Int, Int> = mapOf(
        LibretroButtons.UP to KeyEvent.KEYCODE_DPAD_UP,
        LibretroButtons.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        LibretroButtons.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
        LibretroButtons.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        LibretroButtons.B to KeyEvent.KEYCODE_BUTTON_A,
        LibretroButtons.A to KeyEvent.KEYCODE_BUTTON_B,
        LibretroButtons.Y to KeyEvent.KEYCODE_BUTTON_X,
        LibretroButtons.X to KeyEvent.KEYCODE_BUTTON_Y,
        LibretroButtons.START to KeyEvent.KEYCODE_BUTTON_START,
        LibretroButtons.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
        LibretroButtons.L to KeyEvent.KEYCODE_BUTTON_L1,
        LibretroButtons.R to KeyEvent.KEYCODE_BUTTON_R1,
        LibretroButtons.L2 to KeyEvent.KEYCODE_BUTTON_L2,
        LibretroButtons.R2 to KeyEvent.KEYCODE_BUTTON_R2,
        LibretroButtons.L3 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        LibretroButtons.R3 to KeyEvent.KEYCODE_BUTTON_THUMBR,
    )

    /**
     * Reverse lookup of [hardcodedDefaults]: Android keyCode -> retroButtonId.
     */
    private val defaultKeyToRetro: Map<Int, Int> = hardcodedDefaults.entries.associate { (retro, key) -> key to retro }

    /**
     * Custom reverse mapping (keyCode -> retroButtonId), loaded from the repository.
     * Null means use hardcoded defaults.
     */
    private var customKeyToRetro: Map<Int, Int>? = null

    /**
     * Loads a custom mapping from the repository data.
     * @param retroToKey Map of retroButtonId -> platformKeyCode from the repository
     */
    fun loadCustomMapping(retroToKey: Map<Int, Int>) {
        customKeyToRetro = retroToKey.entries.associate { (retro, key) -> key to retro }
    }

    /**
     * Clears any custom mapping, reverting to hardcoded defaults.
     */
    fun clearCustomMapping() {
        customKeyToRetro = null
    }

    /**
     * Maps an Android [KeyEvent] key code to a libretro button ID.
     * Uses the custom mapping if available, otherwise falls back to the hardcoded default.
     * Returns null if the key code is not a recognized gamepad button.
     */
    fun mapKeyToLibretro(keyCode: Int): Int? {
        return customKeyToRetro?.get(keyCode) ?: defaultKeyToRetro[keyCode]
    }

    /**
     * Normalizes an Android analog axis value (range -1.0..1.0) to a libretro
     * int16 value (range -32768..32767), applying a dead zone to filter noise.
     */
    fun normalizeAxis(value: Float, deadZone: Float = 0.1f): Short {
        val adjusted = if (kotlin.math.abs(value) < deadZone) 0f else value
        return (adjusted * Short.MAX_VALUE).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    /**
     * Normalizes an Android trigger axis value (range 0.0..1.0) to a libretro
     * analog button pressure value (range 0..32767).
     */
    fun normalizeTriggerAxis(value: Float): Short {
        return (value.coerceIn(0f, 1f) * Short.MAX_VALUE).toInt().toShort()
    }

    fun selectPresentTriggerAxis(primary: Float?, fallback: Float?): Float? {
        return primary ?: fallback
    }

    fun resolveTriggerRoute(
        leftTrigger: Float?,
        rightTrigger: Float?,
        mapping: Map<GamepadPosition, Int>,
        digitalThreshold: Float = 0.5f,
    ): TriggerRoute {
        val analogPressures = GamepadButtonResolver.resolveAnalogTriggerPressures(
            l2 = leftTrigger?.let(::normalizeTriggerAxis),
            r2 = rightTrigger?.let(::normalizeTriggerAxis),
            mapping = mapping,
        )
        val digitalPressed = LinkedHashMap<Int, Boolean>()

        fun record(position: GamepadPosition, value: Float?) {
            if (value == null) return
            val retroId = mapping[position] ?: return
            if (retroId !in 0 until GamepadButtonResolver.RETRO_BUTTON_COUNT) return
            digitalPressed[retroId] = (digitalPressed[retroId] ?: false) || value > digitalThreshold
        }

        record(GamepadPosition.L2, leftTrigger)
        record(GamepadPosition.R2, rightTrigger)

        return TriggerRoute(analogPressures, digitalPressed)
    }
}
