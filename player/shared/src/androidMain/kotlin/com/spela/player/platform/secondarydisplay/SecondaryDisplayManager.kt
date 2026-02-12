package com.spela.player.platform.secondarydisplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages detection of secondary displays (e.g. Ayn Thor's 3.92" screen).
 *
 * Uses [DisplayManager] to detect presentation displays and listens for
 * connect/disconnect events. Exposes the current secondary display as a
 * [StateFlow] so Compose UI can reactively respond to display changes.
 */
class SecondaryDisplayManager(context: Context) {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val _secondaryDisplay = MutableStateFlow<Display?>(null)

    /** The currently available secondary (presentation) display, or null if none. */
    val secondaryDisplay: StateFlow<Display?> = _secondaryDisplay.asStateFlow()

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.i(TAG, "Display added: $displayId")
            refreshSecondaryDisplay()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.i(TAG, "Display removed: $displayId")
            refreshSecondaryDisplay()
        }

        override fun onDisplayChanged(displayId: Int) {
            // Display properties changed (e.g. resolution, state) — refresh
            refreshSecondaryDisplay()
        }
    }

    init {
        displayManager.registerDisplayListener(displayListener, null)
        refreshSecondaryDisplay()
    }

    /**
     * Unregister the display listener. Call when the manager is no longer needed.
     */
    fun dispose() {
        displayManager.unregisterDisplayListener(displayListener)
        _secondaryDisplay.value = null
    }

    /**
     * Query DisplayManager for presentation-category displays and update the state.
     * Takes the first available presentation display (most devices have at most one).
     */
    private fun refreshSecondaryDisplay() {
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val display = displays.firstOrNull()
        _secondaryDisplay.value = display
        if (display != null) {
            Log.i(TAG, "Secondary display found: ${display.name} (${display.displayId})")
        } else {
            Log.i(TAG, "No secondary display available")
        }
    }

    companion object {
        private const val TAG = "SecondaryDisplayManager"
    }
}
