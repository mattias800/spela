package com.spela.player.presentation.secondarydisplay

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform abstraction for secondary display support (e.g. Ayn Thor's 3.92" screen).
 *
 * On Android, this wraps [SecondaryDisplayManager] + [Presentation] to detect
 * and show content on a secondary screen.
 * On desktop, this is a no-op (always unavailable).
 *
 * Registered via Koin in platform modules.
 */
interface PlatformSecondaryDisplay {

    /** Whether a secondary display is currently connected and available. */
    val isAvailable: StateFlow<Boolean>

    /**
     * Show the secondary screen content on the external display.
     * Call this when emulation starts and a secondary display is present.
     * No-op if no secondary display is available.
     */
    fun show()

    /**
     * Dismiss any content currently showing on the secondary display.
     * Call this when emulation stops or the secondary display disconnects.
     * Safe to call even if nothing is currently shown.
     */
    fun dismiss()
}
