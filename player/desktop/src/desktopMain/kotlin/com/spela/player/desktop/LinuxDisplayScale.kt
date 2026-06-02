package com.spela.player.desktop

import java.awt.Toolkit
import java.awt.Window
import kotlin.math.abs

/**
 * Linux fractional HiDPI support.
 *
 * AWT's device scale on Linux/X11 is integer-only — fractional values of
 * `sun.java2d.uiScale` and `GDK_SCALE` are floored to 1 by both stock OpenJDK
 * and JBR. On a desktop scaled to e.g. 1.5x (common on KDE), AWT therefore
 * reports scale 1.0 and Compose renders everything ~33% too small.
 *
 * The desktop's real scale is published over XSETTINGS, which AWT exposes as
 * Toolkit desktop properties:
 *  - `gnome.Xft/DPI` — text DPI x1024 (GNOME, or KDE with forced font DPI)
 *  - `gnome.Gdk/UnscaledDPI` — text DPI x1024 before integer window scaling
 *    (KDE publishes the fractional remainder here, e.g. 147456 = 144dpi = 1.5x)
 *  - `gnome.Gdk/WindowScalingFactor` — the integer part AWT already applies
 *
 * When the scale derived from these differs from what AWT detected, the app
 * compensates by overriding [androidx.compose.ui.platform.LocalDensity] at the
 * window root (see Main.kt) — Compose's density is not bound by AWT's
 * integer-only limitation.
 */

/**
 * Pure decision logic: returns the density Compose should use, or null when
 * the AWT-derived density is already correct (or no scale info is available).
 *
 * @param xftDpi          XSETTINGS `Xft/DPI` (x1024), or null if unset
 * @param unscaledDpi     XSETTINGS `Gdk/UnscaledDPI` (x1024), or null if unset
 * @param windowScalingFactor XSETTINGS `Gdk/WindowScalingFactor`, or null
 * @param awtScale        the scale AWT detected (graphics config transform)
 */
internal fun computeLinuxDensityOverride(
    xftDpi: Int?,
    unscaledDpi: Int?,
    windowScalingFactor: Int?,
    awtScale: Float,
): Float? {
    val baseDpi = (xftDpi ?: unscaledDpi)?.takeIf { it > 0 } ?: return null
    val windowScale = (windowScalingFactor ?: 1).coerceAtLeast(1)
    val target = (baseDpi / 1024f) * windowScale / 96f
    // Sanity bounds: ignore corrupt or absurd XSETTINGS values.
    if (target < 0.5f || target > 4f) return null
    // AWT already applies the integer part — only override when they disagree.
    if (abs(target - awtScale) < 0.05f) return null
    return target
}

/**
 * Reads the XSETTINGS desktop properties and the window's AWT scale, and
 * returns the Compose density override, or null when none is needed.
 * Only meaningful on Linux/X11; callers guard on platform.
 */
internal fun detectLinuxDensityOverride(window: Window): Float? {
    val toolkit = Toolkit.getDefaultToolkit()
    val awtScale = window.graphicsConfiguration?.defaultTransform?.scaleX?.toFloat() ?: 1f
    return computeLinuxDensityOverride(
        xftDpi = toolkit.getDesktopProperty("gnome.Xft/DPI") as? Int,
        unscaledDpi = toolkit.getDesktopProperty("gnome.Gdk/UnscaledDPI") as? Int,
        windowScalingFactor = toolkit.getDesktopProperty("gnome.Gdk/WindowScalingFactor") as? Int,
        awtScale = awtScale,
    )
}
