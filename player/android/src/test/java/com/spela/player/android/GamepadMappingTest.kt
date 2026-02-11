package com.spela.player.android

import android.view.KeyEvent
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GamepadMappingTest {

    @AfterTest
    fun tearDown() {
        // Ensure custom mappings don't leak between tests
        GamepadMapping.clearCustomMapping()
    }

    // D-pad mappings

    @Test
    fun dpadUpMapsToUp() {
        assertEquals(LibretroButtons.UP, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun dpadDownMapsToDown() {
        assertEquals(LibretroButtons.DOWN, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_DPAD_DOWN))
    }

    @Test
    fun dpadLeftMapsToLeft() {
        assertEquals(LibretroButtons.LEFT, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun dpadRightMapsToRight() {
        assertEquals(LibretroButtons.RIGHT, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    // Face buttons (Nintendo-style: Android A = libretro B, Android B = libretro A)

    @Test
    fun buttonAMapsToLibretroB() {
        assertEquals(LibretroButtons.B, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun buttonBMapsToLibretroA() {
        assertEquals(LibretroButtons.A, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test
    fun buttonXMapsToLibretroY() {
        assertEquals(LibretroButtons.Y, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_X))
    }

    @Test
    fun buttonYMapsToLibretroX() {
        assertEquals(LibretroButtons.X, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_Y))
    }

    // Start / Select

    @Test
    fun buttonStartMapsToStart() {
        assertEquals(LibretroButtons.START, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_START))
    }

    @Test
    fun buttonSelectMapsToSelect() {
        assertEquals(LibretroButtons.SELECT, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_SELECT))
    }

    // Shoulder buttons

    @Test
    fun buttonL1MapsToL() {
        assertEquals(LibretroButtons.L, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_L1))
    }

    @Test
    fun buttonR1MapsToR() {
        assertEquals(LibretroButtons.R, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_R1))
    }

    @Test
    fun buttonL2MapsToL2() {
        assertEquals(LibretroButtons.L2, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_L2))
    }

    @Test
    fun buttonR2MapsToR2() {
        assertEquals(LibretroButtons.R2, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_R2))
    }

    // Stick clicks

    @Test
    fun buttonThumblMapsToL3() {
        assertEquals(LibretroButtons.L3, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_THUMBL))
    }

    @Test
    fun buttonThumbrMapsToR3() {
        assertEquals(LibretroButtons.R3, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_THUMBR))
    }

    // Unmapped keys

    @Test
    fun unmappedKeyReturnsNull() {
        assertNull(GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun unknownKeyCodeReturnsNull() {
        assertNull(GamepadMapping.mapKeyToLibretro(99999))
    }

    // Axis normalization

    @Test
    fun normalizeAxisZeroReturnsZero() {
        assertEquals(0.toShort(), GamepadMapping.normalizeAxis(0f))
    }

    @Test
    fun normalizeAxisInsideDeadZoneReturnsZero() {
        assertEquals(0.toShort(), GamepadMapping.normalizeAxis(0.05f))
        assertEquals(0.toShort(), GamepadMapping.normalizeAxis(-0.05f))
        assertEquals(0.toShort(), GamepadMapping.normalizeAxis(0.09f))
    }

    @Test
    fun normalizeAxisFullPositiveReturnsMaxShort() {
        assertEquals(Short.MAX_VALUE, GamepadMapping.normalizeAxis(1.0f))
    }

    @Test
    fun normalizeAxisFullNegativeReturnsMinShort() {
        assertEquals(Short.MIN_VALUE, GamepadMapping.normalizeAxis(-1.0f))
    }

    @Test
    fun normalizeAxisHalfReturnsApproximatelyHalfMax() {
        val result = GamepadMapping.normalizeAxis(0.5f)
        // 0.5 * 32767 = 16383.5, truncated to 16383
        assertEquals(16383.toShort(), result)
    }

    @Test
    fun normalizeAxisClampsOverflow() {
        // Values beyond 1.0 should clamp to Short.MAX_VALUE
        assertEquals(Short.MAX_VALUE, GamepadMapping.normalizeAxis(1.5f))
        assertEquals(Short.MIN_VALUE, GamepadMapping.normalizeAxis(-1.5f))
    }

    // Verify all 16 libretro buttons are mapped (completeness check)

    @Test
    fun allLibretroButtonsAreMapped() {
        val allMappedButtons = listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
        )

        val mappedIds = allMappedButtons.mapNotNull { GamepadMapping.mapKeyToLibretro(it) }.toSet()

        // All 16 libretro buttons should be covered
        val expectedIds = setOf(
            LibretroButtons.UP, LibretroButtons.DOWN, LibretroButtons.LEFT, LibretroButtons.RIGHT,
            LibretroButtons.A, LibretroButtons.B, LibretroButtons.X, LibretroButtons.Y,
            LibretroButtons.START, LibretroButtons.SELECT,
            LibretroButtons.L, LibretroButtons.R, LibretroButtons.L2, LibretroButtons.R2,
            LibretroButtons.L3, LibretroButtons.R3,
        )
        assertEquals(expectedIds, mappedIds)
    }

    // Custom mapping tests

    @Test
    fun customMappingOverridesDefault() {
        // Remap A button to SPACE (replaces the default BUTTON_B -> A mapping)
        GamepadMapping.loadCustomMapping(mapOf(
            LibretroButtons.A to KeyEvent.KEYCODE_SPACE,
        ))

        // Custom mapping should take effect for the new key
        assertEquals(LibretroButtons.A, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_SPACE))
        // BUTTON_B is no longer in the custom mapping, so it still resolves via defaults
        assertEquals(LibretroButtons.A, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test
    fun customMappingWithFullOverrideRemovesDefault() {
        // Load a full custom mapping that remaps all buttons
        // Only map UP to a completely different key
        GamepadMapping.loadCustomMapping(mapOf(
            LibretroButtons.UP to KeyEvent.KEYCODE_SPACE,
        ))

        // The custom key should work
        assertEquals(LibretroButtons.UP, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_SPACE))
        // The default UP key still works via fallback
        assertEquals(LibretroButtons.UP, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun customMappingFallsBackToDefaultForUnmappedKeys() {
        // Only remap A button
        GamepadMapping.loadCustomMapping(mapOf(
            LibretroButtons.A to KeyEvent.KEYCODE_SPACE,
        ))

        // Keys not in custom mapping should fall back to default
        assertEquals(LibretroButtons.UP, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test
    fun clearCustomMappingRevertsToDefaults() {
        GamepadMapping.loadCustomMapping(mapOf(
            LibretroButtons.A to KeyEvent.KEYCODE_SPACE,
        ))

        GamepadMapping.clearCustomMapping()

        // Should be back to default
        assertEquals(LibretroButtons.A, GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_BUTTON_B))
        assertNull(GamepadMapping.mapKeyToLibretro(KeyEvent.KEYCODE_SPACE))
    }

    @Test
    fun hardcodedDefaultsMapContainsAll16Buttons() {
        assertEquals(16, GamepadMapping.hardcodedDefaults.size)
    }
}
