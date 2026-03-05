package com.spela.player.platform.secondarydisplay

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.spela.player.presentation.ui.theme.SpelaTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Android [Presentation] subclass that displays Compose content on a secondary display.
 *
 * Implements [LifecycleOwner] and [SavedStateRegistryOwner] so that Compose
 * composition can properly attach to the view tree (required by ComposeView).
 */
class SecondaryDisplayPresentation(
    context: Context,
    display: Display,
    private val content: @Composable () -> Unit,
) : Presentation(context, display), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        window?.apply {
            // Keep secondary screen on while presentation is visible
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Ensure window is focusable so it receives touch input on secondary displays
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@SecondaryDisplayPresentation)
            setViewTreeSavedStateRegistryOwner(this@SecondaryDisplayPresentation)
            isFocusableInTouchMode = true
            setContent {
                SpelaTheme {
                    content()
                }
            }
        }
        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        // Compose requires RESUMED state to process pointer/touch input
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        super.onStop()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        Log.i(TAG, "dispatchTouchEvent: action=${ev.actionMasked} x=${ev.x} y=${ev.y}")
        return super.dispatchTouchEvent(ev)
    }

    override fun onBackPressed() {
        // Suppress back button (gamepad B mapped to KEYCODE_BACK) so it doesn't
        // dismiss the secondary display during gameplay. The presentation is
        // dismissed programmatically when the game stops.
    }

    override fun dismiss() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        super.dismiss()
    }

    companion object {
        private const val TAG = "SecondaryPresentation"
    }
}
