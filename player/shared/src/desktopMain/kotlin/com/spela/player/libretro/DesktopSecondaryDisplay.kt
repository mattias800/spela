package com.spela.player.libretro

import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Desktop no-op implementation of [PlatformSecondaryDisplay].
 * Desktop does not support secondary displays for emulation content.
 */
class DesktopSecondaryDisplay : PlatformSecondaryDisplay {

    override val isAvailable: StateFlow<Boolean> = MutableStateFlow(false)

    override fun show() {
        // No-op on desktop
    }

    override fun dismiss() {
        // No-op on desktop
    }
}
