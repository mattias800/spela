package com.spela.player.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxDisplayScaleTest {

    @Test
    fun `KDE fractional 1_5x - UnscaledDPI 144 with integer window scale 1 overrides to 1_5`() {
        // Observed on KDE Plasma 6 Wayland at 150%: Xft/DPI unset,
        // Gdk/UnscaledDPI=147456 (=144dpi), Gdk/WindowScalingFactor=1, AWT=1.0.
        val result = computeLinuxDensityOverride(
            xftDpi = null,
            unscaledDpi = 147456,
            windowScalingFactor = 1,
            awtScale = 1.0f,
        )
        assertEquals(1.5f, result)
    }

    @Test
    fun `GNOME integer 2x - AWT already applies the scale, no override`() {
        // GNOME at 200%: Xft/DPI=98304 (=96dpi logical), WindowScalingFactor=2,
        // AWT detects 2.0 natively. 96*2/96 = 2.0 == AWT -> no override.
        val result = computeLinuxDensityOverride(
            xftDpi = 98304,
            unscaledDpi = null,
            windowScalingFactor = 2,
            awtScale = 2.0f,
        )
        assertNull(result)
    }

    @Test
    fun `GNOME text scaling 1_25 without window scaling overrides to 1_25`() {
        // GNOME at 100% + text-scaling-factor 1.25: Xft/DPI=122880 (=120dpi).
        val result = computeLinuxDensityOverride(
            xftDpi = 122880,
            unscaledDpi = null,
            windowScalingFactor = 1,
            awtScale = 1.0f,
        )
        assertEquals(1.25f, result)
    }

    @Test
    fun `Xft DPI takes precedence over UnscaledDPI`() {
        val result = computeLinuxDensityOverride(
            xftDpi = 122880, // 120dpi -> 1.25
            unscaledDpi = 147456, // 144dpi -> would be 1.5
            windowScalingFactor = 1,
            awtScale = 1.0f,
        )
        assertEquals(1.25f, result)
    }

    @Test
    fun `no XSETTINGS info - no override`() {
        assertNull(computeLinuxDensityOverride(null, null, null, 1.0f))
    }

    @Test
    fun `scale matching AWT within epsilon - no override`() {
        // 96dpi -> 1.0 == AWT 1.0
        assertNull(computeLinuxDensityOverride(98304, null, 1, 1.0f))
    }

    @Test
    fun `missing window scaling factor defaults to 1`() {
        val result = computeLinuxDensityOverride(
            xftDpi = 147456,
            unscaledDpi = null,
            windowScalingFactor = null,
            awtScale = 1.0f,
        )
        assertEquals(1.5f, result)
    }

    @Test
    fun `absurd DPI values are rejected`() {
        // 9.6dpi -> 0.1 (below sanity floor)
        assertNull(computeLinuxDensityOverride(9830, null, 1, 1.0f))
        // 960dpi -> 10.0 (above sanity ceiling)
        assertNull(computeLinuxDensityOverride(983040, null, 1, 1.0f))
        // zero / negative
        assertNull(computeLinuxDensityOverride(0, null, 1, 1.0f))
        assertNull(computeLinuxDensityOverride(-147456, null, 1, 1.0f))
    }
}
