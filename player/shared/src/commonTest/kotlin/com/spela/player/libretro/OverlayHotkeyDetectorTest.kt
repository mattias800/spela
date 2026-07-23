package com.spela.player.libretro

import com.spela.player.domain.model.GamepadPosition
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayHotkeyDetectorTest {

    @Test
    fun comboRequiresBothPositions() {
        assertFalse(OverlayHotkey.isCombo(setOf(GamepadPosition.SELECT)))
        assertFalse(OverlayHotkey.isCombo(setOf(GamepadPosition.START)))
        assertTrue(OverlayHotkey.isCombo(setOf(GamepadPosition.SELECT, GamepadPosition.START)))
        assertTrue(
            OverlayHotkey.isCombo(
                setOf(GamepadPosition.SELECT, GamepadPosition.START, GamepadPosition.SOUTH),
            ),
            "other buttons held at the same time must not block the combo",
        )
    }

    @Test
    fun firesOnceAfterTheHoldThreshold() {
        val detector = OverlayHotkeyDetector(holdMillis = 300)

        assertFalse(detector.update(nowMillis = 0, comboHeld = true), "must not fire immediately")
        assertFalse(detector.update(nowMillis = 299, comboHeld = true), "must not fire before the threshold")
        assertTrue(detector.update(nowMillis = 300, comboHeld = true), "fires when the threshold is crossed")
        assertFalse(detector.update(nowMillis = 400, comboHeld = true), "must not re-fire while still held")
        assertFalse(detector.update(nowMillis = 5000, comboHeld = true), "must not re-fire on a long hold")
    }

    @Test
    fun quickTapDoesNotFireSoSoftResetCombosStillReachTheCore() {
        val detector = OverlayHotkeyDetector(holdMillis = 300)

        assertFalse(detector.update(nowMillis = 0, comboHeld = true))
        assertFalse(detector.update(nowMillis = 120, comboHeld = true))
        assertFalse(detector.update(nowMillis = 130, comboHeld = false), "released before the threshold")
        assertFalse(detector.isLatched)
    }

    @Test
    fun latchesWhileHeldAndClearsOnRelease() {
        val detector = OverlayHotkeyDetector(holdMillis = 300)

        detector.update(nowMillis = 0, comboHeld = true)
        assertFalse(detector.isLatched, "not latched before firing")

        assertTrue(detector.update(nowMillis = 300, comboHeld = true))
        assertTrue(detector.isLatched, "latched so the core stops seeing the buttons")

        detector.update(nowMillis = 900, comboHeld = false)
        assertFalse(detector.isLatched, "release clears the latch")
    }

    @Test
    fun rearmsAfterAFullReleaseSoTheOverlayCanBeToggledAgain() {
        val detector = OverlayHotkeyDetector(holdMillis = 300)

        detector.update(nowMillis = 0, comboHeld = true)
        assertTrue(detector.update(nowMillis = 300, comboHeld = true))
        detector.update(nowMillis = 700, comboHeld = false)

        assertFalse(detector.update(nowMillis = 800, comboHeld = true), "hold restarts from the new press")
        assertTrue(detector.update(nowMillis = 1100, comboHeld = true), "fires again after a fresh hold")
    }

    @Test
    fun holdTimerRestartsWhenTheComboIsBrokenAndRepressed() {
        val detector = OverlayHotkeyDetector(holdMillis = 300)

        detector.update(nowMillis = 0, comboHeld = true)
        detector.update(nowMillis = 200, comboHeld = false)
        // Re-pressed at 250: the earlier 200ms must not count toward the hold.
        detector.update(nowMillis = 250, comboHeld = true)

        assertFalse(detector.update(nowMillis = 450, comboHeld = true), "elapsed is measured from the re-press")
        assertTrue(detector.update(nowMillis = 550, comboHeld = true))
    }
}
