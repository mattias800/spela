package com.spela.player.android

import android.content.pm.ActivityInfo
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.spela.player.domain.model.ControllerClassifier
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.libretro.AndroidGamepadNormalizer
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.App
import com.spela.player.presentation.ui.gamepad.ComposeFocusBridge
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.viewmodel.LibretroButtons
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    companion object {
        /** Set by instrumentation tests to disable continuous animations. */
        @JvmStatic
        var isTestMode = false
    }

    private val libretroController: LibretroController by inject()
    private val androidController: AndroidLibretroController?
        get() = libretroController as? AndroidLibretroController

    private val navigationViewModel: NavigationViewModel by inject()
    private val emulationViewModel: EmulationViewModel by inject()
    private val keyMappingViewModel: KeyMappingViewModel by inject()
    private val gamepadPortManager: GamepadPortManager by inject()
    private val settingsViewModel: SettingsViewModel by inject()
    private val preferencesRepository: PreferencesRepository by inject()

    /** True when gamepad input should go to libretro (game running, overlay not shown). */
    private val isEmulationConsuming: Boolean
        get() {
            val navState = navigationViewModel.state.value
            val emuState = emulationViewModel.state.value
            return navState.showInGameOverlay && emuState.isRunning && !emuState.showOverlay
        }

    /** True when a game is running (regardless of overlay state). */
    private val isGameRunning: Boolean
        get() {
            val navState = navigationViewModel.state.value
            val emuState = emulationViewModel.state.value
            return navState.showInGameOverlay && emuState.isRunning
        }

    // Track analog-to-dpad state for UI navigation
    private var analogDpadLeft = false
    private var analogDpadRight = false
    private var analogDpadUp = false
    private var analogDpadDown = false


    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            // Register hot-plugged gamepads immediately so the controller list
            // (Settings → Controls, #1359) shows them without a button press.
            // Non-gamepad devices are filtered out inside ensureDeviceConnected.
            ensureDeviceConnected(deviceId)
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            gamepadPortManager.disconnectDevice(deviceId)
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            // No action needed
        }
    }

    /**
     * Registers every already-connected gamepad up front (#1359) so the
     * controller list is populated on entry, not only after the first input.
     * Idempotent and gamepad-filtered (via [ensureDeviceConnected]).
     */
    private fun enumerateConnectedGamepads() {
        for (deviceId in InputDevice.getDeviceIds()) {
            ensureDeviceConnected(deviceId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val inputManager = getSystemService(INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(inputDeviceListener, null)
        enumerateConnectedGamepads()

        // Apply initial orientation lock
        applyOrientationLock(preferencesRepository.getOrientationLock())

        // Observe orientation lock changes so they apply immediately
        lifecycleScope.launch {
            settingsViewModel.state
                .map { it.orientationLock }
                .distinctUntilChanged()
                .collect { mode -> applyOrientationLock(mode) }
        }

        // Hide system bars during gameplay, restore when back in UI
        lifecycleScope.launch {
            navigationViewModel.state
                .map { it.showInGameOverlay }
                .distinctUntilChanged()
                .collect { isInGame ->
                    val controller = window.insetsController ?: return@collect
                    if (isInGame) {
                        controller.hide(WindowInsets.Type.systemBars())
                        controller.systemBarsBehavior =
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        controller.show(WindowInsets.Type.systemBars())
                    }
                }
        }

        setContent {
            // Disable continuous animations during instrumentation tests.
            // Compose test's waitForIdle() syncs with Espresso which checks the
            // Choreographer — continuous animations keep it busy, causing
            // AppNotIdleException. LocalAnimationsEnabled = false disables
            // infinite transitions, gradient glow, and ambient blobs.
            if (isTestMode) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.spela.player.presentation.ui.components.LocalAnimationsEnabled provides false,
                ) {
                    App()
                }
            } else {
                App()
            }
        }
    }

    override fun onPause() {
        val state = emulationViewModel.state.value
        if (state.isRunning && !state.isPaused) {
            emulationViewModel.onIntent(EmulationIntent.LifecyclePause)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (emulationViewModel.state.value.isLifecyclePaused) {
            emulationViewModel.onIntent(EmulationIntent.LifecycleResume)
        }
    }

    override fun onDestroy() {
        val inputManager = getSystemService(INPUT_SERVICE) as InputManager
        inputManager.unregisterInputDeviceListener(inputDeviceListener)
        super.onDestroy()
    }

    /**
     * Ensures a gamepad device is assigned to a port. If it's a new device,
     * assigns it and loads its key mapping for the current console.
     * Returns the assigned port, or -1 if all ports are full / device is
     * not a gamepad.
     *
     * Source-flags filter: Android dispatches `onKeyDown` for **any**
     * KEYBOARD-class input device — hardware volume keys (`gpio-keys`),
     * power button (`pmic_pwrkey`), audio-jack mic button, even the
     * primary touchscreen on devices that re-route gesture-recognised
     * keystrokes through it (the AYN Thor's `fts_ts` exposes
     * KEY_UP/DOWN/LEFT/RIGHT/POWER, see #1165). Without filtering we'd
     * allocate a player-port for every distinct deviceId that ever
     * produced a key event, so the player-N indicator ended up showing
     * 2 controllers on the Thor with only the internal gamepad present.
     *
     * The narrowest correct filter is `SOURCE_GAMEPAD` — every real
     * controller has this bit set; nothing else does. Joystick-only
     * devices (rare but possible) are also accepted on `SOURCE_JOYSTICK`
     * for defense in depth.
     */
    private fun ensureDeviceConnected(deviceId: Int): Int {
        val existingPort = gamepadPortManager.getPort(deviceId)
        if (existingPort >= 0) return existingPort

        val device = InputDevice.getDevice(deviceId) ?: return -1
        if (!isGamepadInputDevice(device)) return -1

        val deviceName = device.name ?: "Gamepad $deviceId"
        // Detect the controller style (#1334) so input is normalized positionally
        // and the identity display is correct. Vendor/product with a name fallback.
        val style = ControllerClassifier.fromVendorProduct(device.vendorId, device.productId, deviceName)
        // descriptor is stable per physical controller across reconnects (#1359),
        // so player-slot assignments persist. Fall back to the name if absent.
        val stableKey = device.descriptor ?: deviceName
        val port = gamepadPortManager.connectDevice(deviceId, deviceName, style, stableKey)
        if (port >= 0) {
            val consoleId = emulationViewModel.state.value.consoleId
            if (consoleId.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        // Gamepad input now flows through the positional mapping
                        // layer (key code → GamepadPosition → RetroPad).
                        gamepadPortManager.loadGamepadMappingForPort(port, consoleId)
                    } catch (_: Exception) {
                        // Best effort
                    }
                }
            }
        }
        return port
    }

    /**
     * True when [device] is a real gamepad-shaped input — both
     * SOURCE_GAMEPAD (face buttons / dpad / start / select) and
     * SOURCE_JOYSTICK (analog sticks / triggers / hat) qualify. Devices
     * that are KEYBOARD-only (volume keys, power button, jack-button,
     * touchscreen accessibility keystrokes) are filtered out so they
     * don't claim player ports.
     */
    private fun isGamepadInputDevice(device: InputDevice): Boolean {
        val sources = device.sources
        val isGamepad = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val isJoystick = sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        return isGamepad || isJoystick
    }

    /**
     * Routes a test button to the live input tester (#1355/#1359) — but ONLY for
     * the specific controller currently under test (the one whose detail tester is
     * focused), and never the D-pad. Returns true when it captured the event so the
     * caller consumes it (the button must not also navigate/select). When no tester
     * is focused, or the event is from a different controller, this is a no-op and
     * input routing is unchanged, so navigation is never disrupted. The D-pad always
     * falls through to navigation.
     */
    private fun captureTestInput(event: KeyEvent?, keyCode: Int, pressed: Boolean): Boolean {
        val target = gamepadPortManager.testCaptureDeviceId.value ?: return false
        val deviceId = event?.deviceId ?: return false
        if (deviceId != target) return false
        val position = AndroidGamepadNormalizer.normalize(keyCode) ?: return false
        if (position.isDpad) return false
        gamepadPortManager.reportPositionInput(deviceId, position, pressed)
        return true
    }

    /**
     * Hold-to-bind capture (#1377): while the per-console mapping editor has a
     * binding session open, capture EVERY gamepad button — including the D-pad —
     * from ANY controller and feed it to the binder, consuming the event so a
     * press only binds (never navigates). No-op when no session is active.
     */
    private fun captureBindInput(event: KeyEvent?, keyCode: Int, pressed: Boolean): Boolean {
        if (!gamepadPortManager.bindCaptureActive.value) return false
        val deviceId = event?.deviceId ?: return false
        val position = AndroidGamepadNormalizer.normalize(keyCode) ?: return false
        gamepadPortManager.reportBindPosition(deviceId, position, pressed)
        return true
    }

    /** Key codes that are gamepad-relevant and should be captured during key mapping. */
    private fun isGamepadCapturable(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_MODE,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER -> true
        else -> false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Register the source device as a player port if it's a real
        // gamepad (SOURCE_GAMEPAD / SOURCE_JOYSTICK — see
        // ensureDeviceConnected). Done at the top of dispatch, before
        // any state-machine branch, so the on-screen player-N indicator
        // reflects "the user has used this controller" rather than the
        // narrower "the user has used this controller during
        // emulation". Without this, the pill-menu bumper glyphs at the
        // top of UI screens fall back to keyboard ([ / ]) instead of
        // L1 / R1 until the user has actually entered a game (#1165).
        //
        // No-op for non-gamepad devices — ensureDeviceConnected's own
        // filter rejects volume keys, power button, touchscreen-
        // gesture keystrokes, etc.
        if (event != null) {
            ensureDeviceConnected(event.deviceId)
        }

        // Hold-to-bind capture (#1377): when the mapping editor is awaiting a
        // press, capture every button (incl. D-pad) from any pad and consume it.
        if (captureBindInput(event, keyCode, pressed = true)) return true

        // Live input tester (#1355): when its element is focused, capture test
        // buttons (face/shoulder/trigger/stick — never the D-pad) and consume
        // them so they don't navigate. No-op otherwise.
        if (captureTestInput(event, keyCode, pressed = true)) return true

        // Key mapping capture: when in listening mode, intercept gamepad buttons
        if (keyMappingViewModel.state.value.currentMappingButton != null && isGamepadCapturable(keyCode)) {
            keyMappingViewModel.onIntent(KeyMappingIntent.CaptureButton(keyCode))
            return true
        }

        // State 1: Game running, overlay hidden — send buttons to emulation core
        if (isEmulationConsuming && event != null) {
            val deviceId = event.deviceId
            val port = ensureDeviceConnected(deviceId)
            if (port < 0) return super.onKeyDown(keyCode, event)

            val buttonId = gamepadPortManager.mapGamepadKeyToLibretro(port, keyCode)
            if (buttonId != null) {
                androidController?.let {
                    it.setButton(port, buttonId, true)
                    it.notifyPhysicalControllerInput()
                }
                gamepadPortManager.reportActivity(port)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        // State 2: Game running, overlay shown — navigate the overlay
        if (isGameRunning) {
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_B -> {
                    emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_A -> {
                    val now = SystemClock.uptimeMillis()
                    val remapped = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                    return super.dispatchKeyEvent(remapped)
                }
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> return true
            }
            // Let D-pad propagate to Compose for overlay navigation
            return super.onKeyDown(keyCode, event)
        }

        // State 3: Not in game — UI mode: remap gamepad buttons for Compose navigation
        gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> {
                val now = SystemClock.uptimeMillis()
                val remapped = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                return super.dispatchKeyEvent(remapped)
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                navigationViewModel.onIntent(NavigationIntent.GoBack)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                navigationViewModel.onIntent(NavigationIntent.PreviousSection)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                navigationViewModel.onIntent(NavigationIntent.NextSection)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                val navState = navigationViewModel.state.value
                if (navState.currentScreen !is SpScreen.GlobalSearch) {
                    navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GlobalSearch))
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                // Force Compose into keyboard input mode so the
                // focus-restoration snapshotFlow listener in
                // `focusRestoreItem` fires and brings focus back to
                // the last-saved item. On AYN Thor (and possibly other
                // handhelds), hardware d-pad events arrive with
                // source=0 and Compose's `ComposeView.dispatchKeyEvent`
                // does NOT update `inputModeManager.inputMode` for
                // them — verified by absence of matching `[ImDbg]`
                // logs during a session where every d-pad press fired
                // `onKeyDown`. Without the mode flip the listener
                // never wakes up, so the user is stuck after any
                // touch-mode interaction until they press A (which is
                // remapped to DPAD_CENTER and re-dispatched, taking a
                // path that DOES trigger the mode flip). We do the
                // flip ourselves here via the bridge.
                //
                // We don't eat the event — after the bridge call we
                // let `super.onKeyDown` propagate normally so the
                // key also drives carousel/grid navigation. The user
                // sees one press both restore focus AND navigate.
                ComposeFocusBridge.requestKeyboardMode?.invoke()
            }
        }

        // Let D-pad and other keys propagate to Compose
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Hold-to-bind capture (#1377): release feeds the binder (resets the hold).
        if (captureBindInput(event, keyCode, pressed = false)) return true

        // Live input tester (#1355): release the captured test button.
        if (captureTestInput(event, keyCode, pressed = false)) return true

        // Key mapping capture: consume key-up for gamepad buttons when in listening mode
        if (keyMappingViewModel.state.value.currentMappingButton != null && isGamepadCapturable(keyCode)) {
            return true
        }

        // State 1: Game running, overlay hidden — release button in core
        if (isEmulationConsuming && event != null) {
            val deviceId = event.deviceId
            val port = gamepadPortManager.getPort(deviceId)
            if (port < 0) return super.onKeyUp(keyCode, event)

            val buttonId = gamepadPortManager.mapGamepadKeyToLibretro(port, keyCode)
            if (buttonId != null) {
                androidController?.let {
                    it.setButton(port, buttonId, false)
                    it.notifyPhysicalControllerInput()
                }
                return true
            }
            return super.onKeyUp(keyCode, event)
        }

        // State 2: Game running, overlay shown — consume B/A/L1/R1 events
        if (isGameRunning) {
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_B -> return true
                KeyEvent.KEYCODE_BUTTON_A -> {
                    val now = SystemClock.uptimeMillis()
                    val remapped = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                    return super.dispatchKeyEvent(remapped)
                }
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> return true
            }
            return super.onKeyUp(keyCode, event)
        }

        // State 3: Not in game — UI mode: consume gamepad buttons we handled in onKeyDown
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> {
                val now = SystemClock.uptimeMillis()
                val remapped = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0)
                return super.dispatchKeyEvent(remapped)
            }
            KeyEvent.KEYCODE_BUTTON_B -> return true
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> return true
            KeyEvent.KEYCODE_BUTTON_SELECT -> return true
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onGenericMotionEvent(event)

        val isJoystickOrGamepad =
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD

        if (!isJoystickOrGamepad || event.action != MotionEvent.ACTION_MOVE) {
            return super.onGenericMotionEvent(event)
        }

        // Register the source device as a player port — mirrors the
        // onKeyDown path so analog-stick / trigger movement also
        // populates the on-screen indicator (#1165). No-op for non-
        // gamepad sources thanks to ensureDeviceConnected's filter.
        ensureDeviceConnected(event.deviceId)

        if (isEmulationConsuming) {
            val controller = androidController ?: return super.onGenericMotionEvent(event)
            val port = ensureDeviceConnected(event.deviceId)
            if (port < 0) return super.onGenericMotionEvent(event)

            controller.notifyPhysicalControllerInput()
            gamepadPortManager.reportActivity(port)

            val leftX = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_X))
            val leftY = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_Y))
            val rightX = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_Z))
            val rightY = GamepadMapping.normalizeAxis(event.getAxisValue(MotionEvent.AXIS_RZ))

            controller.setAnalog(port, 0, 0, leftX)
            controller.setAnalog(port, 0, 1, leftY)
            controller.setAnalog(port, 1, 0, rightX)
            controller.setAnalog(port, 1, 1, rightY)

            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            controller.setButton(port, LibretroButtons.LEFT, hatX < -0.5f)
            controller.setButton(port, LibretroButtons.RIGHT, hatX > 0.5f)
            controller.setButton(port, LibretroButtons.UP, hatY < -0.5f)
            controller.setButton(port, LibretroButtons.DOWN, hatY > 0.5f)

            return true
        }

        // UI mode: convert analog stick to D-pad events for focus navigation
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        val threshold = 0.5f
        val nowLeft = x < -threshold || hatX < -threshold
        val nowRight = x > threshold || hatX > threshold
        val nowUp = y < -threshold || hatY < -threshold
        val nowDown = y > threshold || hatY > threshold

        // Right stick scrolling: convert right analog stick Y-axis to scroll events
        val rY = event.getAxisValue(MotionEvent.AXIS_RZ)

        // Only switch to gamepad mode when there's actual meaningful input,
        // not on zero-value motion events from stick drift
        if (nowLeft || nowRight || nowUp || nowDown || Math.abs(rY) > 0.15f) {
            gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        }

        if (nowLeft && !analogDpadLeft) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN)
        if (!nowLeft && analogDpadLeft) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_UP)
        if (nowRight && !analogDpadRight) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN)
        if (!nowRight && analogDpadRight) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_UP)
        if (nowUp && !analogDpadUp) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN)
        if (!nowUp && analogDpadUp) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_UP)
        if (nowDown && !analogDpadDown) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN)
        if (!nowDown && analogDpadDown) dispatchDpadEvent(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_UP)

        analogDpadLeft = nowLeft
        analogDpadRight = nowRight
        analogDpadUp = nowUp
        analogDpadDown = nowDown

        if (Math.abs(rY) > 0.15f) {
            val props = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
            val coords = MotionEvent.PointerCoords().apply {
                setAxisValue(MotionEvent.AXIS_VSCROLL, -rY * 0.5f)
            }
            val scrollEvent = MotionEvent.obtain(
                event.downTime, event.eventTime, MotionEvent.ACTION_SCROLL,
                1, arrayOf(props), arrayOf(coords),
                0, 0, 1f, 1f, event.deviceId, 0, InputDevice.SOURCE_MOUSE, 0
            )
            super.dispatchGenericMotionEvent(scrollEvent)
            scrollEvent.recycle()
        }

        return true
    }

    private fun applyOrientationLock(mode: String) {
        requestedOrientation = when (mode) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun dispatchDpadEvent(keyCode: Int, action: Int) {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, action, keyCode, 0)
        super.dispatchKeyEvent(event)
    }

}
