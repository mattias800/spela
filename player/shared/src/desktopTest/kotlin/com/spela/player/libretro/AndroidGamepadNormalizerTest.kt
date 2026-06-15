package com.spela.player.libretro

import com.spela.player.domain.model.DefaultGamepadMapping
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.repository.GamepadMappingRepository
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidGamepadNormalizerTest {

    // android.view.KeyEvent values.
    private val BUTTON_A = 96
    private val BUTTON_B = 97
    private val BUTTON_X = 99
    private val BUTTON_Y = 100
    private val DPAD_UP = 19
    private val VOLUME_UP = 24 // not a gamepad button

    @Test
    fun mapsStandardFaceButtonsPositionally() {
        assertEquals(GamepadPosition.SOUTH, AndroidGamepadNormalizer.normalize(BUTTON_A))
        assertEquals(GamepadPosition.EAST, AndroidGamepadNormalizer.normalize(BUTTON_B))
        assertEquals(GamepadPosition.WEST, AndroidGamepadNormalizer.normalize(BUTTON_X))
        assertEquals(GamepadPosition.NORTH, AndroidGamepadNormalizer.normalize(BUTTON_Y))
        assertEquals(GamepadPosition.DPAD_UP, AndroidGamepadNormalizer.normalize(DPAD_UP))
    }

    @Test
    fun ignoresNonGamepadKeys() {
        assertNull(AndroidGamepadNormalizer.normalize(VOLUME_UP))
    }

    @Test
    fun defaultPathReproducesHistoricalAndroidMapping() {
        // Historically Android KEYCODE_BUTTON_A -> RetroPad B, BUTTON_B -> RetroPad A.
        val mgr = GamepadPortManager(FakeKeyMappingRepo())
        mgr.connectDevice(deviceId = 1, deviceName = "Pad")
        // No per-console mapping loaded -> defaults apply.
        assertEquals(LibretroButtons.B, mgr.mapGamepadKeyToLibretro(0, BUTTON_A))
        assertEquals(LibretroButtons.A, mgr.mapGamepadKeyToLibretro(0, BUTTON_B))
        assertNull(mgr.mapGamepadKeyToLibretro(0, VOLUME_UP))
    }

    /**
     * The guiding example as a brand-independence regression: with a per-console
     * mapping of SOUTH->A and WEST->B, the standard positional key codes (which
     * any spec-compliant pad reports for those physical buttons) yield the NES
     * remap through the Android path — proving the positional layer is brand-neutral.
     */
    @Test
    fun guidingExampleNesSouthToAWestToB() = runTest {
        val guidingMapping = DefaultGamepadMapping.POSITION_TO_RETRO + mapOf(
            GamepadPosition.SOUTH to LibretroButtons.A,
            GamepadPosition.WEST to LibretroButtons.B,
        )
        val mgr = GamepadPortManager(
            FakeKeyMappingRepo(),
            gamepadMappingRepository = FakeGamepadMappingRepo(guidingMapping),
        )
        mgr.connectDevice(deviceId = 1, deviceName = "Pad")
        mgr.loadGamepadMappingForPort(0, "nes")

        // Bottom physical button (KEYCODE_BUTTON_A = SOUTH) -> NES A.
        assertEquals(LibretroButtons.A, mgr.mapGamepadKeyToLibretro(0, BUTTON_A))
        // Left physical button (KEYCODE_BUTTON_X = WEST) -> NES B.
        assertEquals(LibretroButtons.B, mgr.mapGamepadKeyToLibretro(0, BUTTON_X))
    }

    private class FakeGamepadMappingRepo(
        private val mapping: Map<GamepadPosition, Int>,
    ) : GamepadMappingRepository {
        override suspend fun getEffectiveMapping(consoleId: String, port: Int) = mapping
        override suspend fun setBinding(consoleId: String, port: Int, position: GamepadPosition, retroButtonId: Int) {}
        override suspend fun bindPositionExclusive(consoleId: String, port: Int, position: GamepadPosition, retroButtonId: Int) {}
        override suspend fun resetToDefault(consoleId: String, port: Int) {}
        override fun getDefaultMapping() = DefaultGamepadMapping.POSITION_TO_RETRO
    }

    private class FakeKeyMappingRepo : KeyMappingRepository {
        override suspend fun getMappingForConsole(consoleId: String, port: Int): KeyMappingProfile? = null
        override suspend fun setBinding(consoleId: String, port: Int, retroButtonId: Int, platformKeyCode: Int) {}
        override suspend fun resetToDefault(consoleId: String, port: Int) {}
        override suspend fun clearBinding(consoleId: String, port: Int, retroButtonId: Int) {}
        override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<Int, Int> = emptyMap()
        override fun getDefaultMapping(): Map<Int, Int> = emptyMap()
        override fun getAvailablePresets(): List<KeyMappingPreset> = emptyList()
        override suspend fun applyPreset(presetId: String) {}
        override suspend fun ensureDefaultsApplied() {}
        override suspend fun getEffectiveMappingForGame(gameId: String, consoleId: String, port: Int): Map<Int, Int> = emptyMap()
        override suspend fun setGameMapping(gameId: String, bindings: Map<Int, Int>) {}
        override suspend fun clearGameMapping(gameId: String) {}
        override suspend fun hasGameMapping(gameId: String): Boolean = false
    }
}
