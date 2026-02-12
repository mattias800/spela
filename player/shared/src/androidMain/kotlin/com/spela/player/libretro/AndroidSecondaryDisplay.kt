package com.spela.player.libretro

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.presentation.ui.screen.SecondaryScreenContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android implementation of [PlatformSecondaryDisplay].
 *
 * Wraps [SecondaryDisplayManager] for display detection and
 * [SecondaryDisplayPresentation] for showing Compose content on it.
 */
class AndroidSecondaryDisplay(
    private val context: Context,
    private val displayManager: SecondaryDisplayManager,
    private val scope: CoroutineScope,
) : PlatformSecondaryDisplay {

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private var presentation: SecondaryDisplayPresentation? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        scope.launch {
            displayManager.secondaryDisplay.collect { display ->
                val available = display != null
                _isAvailable.value = available
                if (!available) {
                    mainHandler.post { dismissInternal() }
                }
                Log.i(TAG, "Secondary display available: $available")
            }
        }
    }

    override fun show() {
        mainHandler.post {
            if (presentation != null) return@post
            val display = displayManager.secondaryDisplay.value ?: return@post

            try {
                val pres = SecondaryDisplayPresentation(
                    context = context,
                    display = display,
                    content = { SecondaryScreenContent() },
                )
                pres.show()
                presentation = pres
                Log.i(TAG, "Secondary display presentation shown")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show secondary display presentation", e)
            }
        }
    }

    override fun dismiss() {
        mainHandler.post { dismissInternal() }
    }

    private fun dismissInternal() {
        presentation?.let {
            try {
                it.dismiss()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dismiss presentation", e)
            }
        }
        presentation = null
    }

    companion object {
        private const val TAG = "AndroidSecondaryDisplay"
    }
}
