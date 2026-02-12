package com.spela.player.libretro

import kotlin.test.Test
import kotlin.test.assertFalse

class DesktopSecondaryDisplayTest {

    @Test
    fun isAvailableIsAlwaysFalse() {
        val display = DesktopSecondaryDisplay()
        assertFalse(display.isAvailable.value)
    }

    @Test
    fun showIsNoOp() {
        val display = DesktopSecondaryDisplay()
        // Should not throw or change state
        display.show()
        assertFalse(display.isAvailable.value)
    }

    @Test
    fun dismissIsNoOp() {
        val display = DesktopSecondaryDisplay()
        // Should not throw or change state
        display.dismiss()
        assertFalse(display.isAvailable.value)
    }

    @Test
    fun isAvailableRemainsFalseAfterShowAndDismiss() {
        val display = DesktopSecondaryDisplay()
        display.show()
        assertFalse(display.isAvailable.value)
        display.dismiss()
        assertFalse(display.isAvailable.value)
    }
}
