package com.spela.player.libretro

import com.spela.player.domain.model.DefaultGamepadMapping
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.model.controllerStyleFromSdlType
import com.spela.player.presentation.navigation.NavigationEventBus
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.viewmodel.LibretroController
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Polls connected gamepads via SDL2 (JNI) and routes their input
 * to the emulation controller through [GamepadPortManager].
 *
 * Runs a coroutine at ~120 Hz on a dedicated dispatcher.
 * Handles device connect/disconnect automatically.
 */
class DesktopGamepadPoller(
    private val jni: LibretroJni,
    private val gamepadPortManager: GamepadPortManager,
    private val controller: LibretroController,
    private val navigationEventBus: NavigationEventBus? = null,
    /** True while a game is open; suppresses UI-navigation synth (see [GamepadUiNavigator]). */
    private val isInGame: () -> Boolean = { false },
) {
    companion object {
        private const val POLL_INTERVAL_MS = 8L // ~120 Hz

        /** Axis dead zone (SDL range is -32768..32767, ~15% dead zone). */
        private const val AXIS_DEADZONE = 4800

        /** SDL axis magnitude, for normalizing to -1..1 (#1362 right-stick scroll). */
        private const val AXIS_RANGE = 32768f

        /** Number of axes: LX, LY, RX, RY, TriggerL, TriggerR. */
        private const val NUM_AXES = 6

        /** Trigger threshold to consider as digital press. */
        private const val TRIGGER_THRESHOLD = 8000
    }

    private var pollJob: Job? = null
    private var initialized = false

    /**
     * SDL must be initialized and polled from the SAME thread — its event pump
     * (which delivers controller hot-plug events) is thread-affine. A coroutine
     * on Dispatchers.IO hops threads across delay(), which silently breaks
     * hot-plug detection. Pin everything to one dedicated thread.
     */
    private val pollDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "spela-gamepad-poller").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    /** SDL controller IDs currently tracked, mapped to port manager device IDs. */
    private val knownControllers = mutableSetOf<Int>()

    /** Translates controller input into UI navigation key events (menus only). */
    private val uiNavigator = GamepadUiNavigator(
        navigationEventBus = navigationEventBus,
        isInGame = isInGame,
        onGamepadInput = { gamepadPortManager.setInputMode(InputMode.GAMEPAD) },
    )

    fun start(scope: CoroutineScope) {
        if (pollJob != null) return

        pollJob = scope.launch(pollDispatcher) {
            initialized = jni.nativeGamepadInit()
            if (!initialized) {
                println("[GamepadPoller] SDL2 gamepad init failed (SDL2 may not be available)")
                return@launch
            }
            println("[GamepadPoller] SDL2 gamepad initialized")

            while (isActive) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        if (initialized) {
            jni.nativeGamepadShutdown()
            initialized = false
        }
        knownControllers.clear()
    }

    private fun poll() {
        val states = jni.nativeGamepadPoll() ?: return
        val desktopController = controller as? DesktopLibretroController ?: return

        // Track which controllers are present this frame
        val currentIds = mutableSetOf<Int>()

        // Largest right-stick Y deflection across all pads, for UI viewport
        // scrolling (#1362) — works for assigned and unassigned controllers alike.
        var uiScrollY = 0f

        for (state in states) {
            currentIds.add(state.controllerId)

            // Connect new devices. Prefer the per-unit serial as the persistence
            // stable key (#1361) so two identical pads keep distinct player slots;
            // fall back to the name when the pad exposes no serial.
            if (state.controllerId !in knownControllers) {
                knownControllers.add(state.controllerId)
                gamepadPortManager.connectDevice(
                    state.controllerId,
                    state.name,
                    controllerStyleFromSdlType(state.type),
                    stableKey = state.serial.ifBlank { state.name },
                )
            }

            // state.buttons is indexed by GamepadPosition ordinal (input layer).
            // Triggers are analog (axes 4/5) — fold them into the L2/R2 position
            // slots, which the C bridge leaves unset.
            val positionPressed = BooleanArray(GamepadPosition.entries.size)
            for (i in 0 until minOf(positionPressed.size, state.buttons.size)) {
                positionPressed[i] = state.buttons[i]
            }
            if (state.axes.size >= NUM_AXES) {
                positionPressed[GamepadPosition.L2.ordinal] = state.axes[4] > TRIGGER_THRESHOLD
                positionPressed[GamepadPosition.R2.ordinal] = state.axes[5] > TRIGGER_THRESHOLD
            }

            // Live input tester (#1355/#1359): only for the controller currently
            // under test, report pressed test positions (D-pad excluded — it always
            // navigates). Done before the port check so an *unassigned* (cleared)
            // controller is still fully testable.
            if (gamepadPortManager.testCaptureDeviceId.value == state.controllerId) {
                gamepadPortManager.reportPressedPositions(
                    state.controllerId,
                    GamepadPosition.entries.filterTo(mutableSetOf()) {
                        positionPressed[it.ordinal] && !it.isDpad
                    },
                )
            }

            // Hold-to-bind capture (#1377): any controller, INCLUDING the D-pad, so
            // the user can rebind D-pad directions. Reported for every controller —
            // the editor binds with whichever pad the user holds a button on.
            if (gamepadPortManager.bindCaptureActive.value) {
                gamepadPortManager.reportBindPressedPositions(
                    state.controllerId,
                    GamepadPosition.entries.filterTo(mutableSetOf()) {
                        positionPressed[it.ordinal]
                    },
                )
            }

            // Right-stick viewport scroll (#1362): track the largest right-stick Y
            // deflection across pads (before the port check, so an unassigned pad
            // still scrolls the UI). The UI's RightStickScroll effect consumes it.
            if (state.axes.size >= NUM_AXES) {
                val ry = applyDeadzone(state.axes[3]) / AXIS_RANGE
                if (abs(ry) > abs(uiScrollY)) uiScrollY = ry
            }

            // Emulation routing requires an assigned player port; unassigned
            // controllers stop here (they only feed the tester above).
            val port = gamepadPortManager.getPort(state.controllerId)
            if (port < 0) continue

            var hasInput = false

            // Apply the configurable mapping layer (position -> RetroPad), with
            // fan-in handled so a released position can't clobber another's press.
            val mapping = gamepadPortManager.getGamepadMapping(port)
                ?: DefaultGamepadMapping.POSITION_TO_RETRO
            val retroPressed = GamepadButtonResolver.resolve(positionPressed, mapping)
            for (retroId in retroPressed.indices) {
                desktopController.setButton(port, retroId, retroPressed[retroId])
                if (retroPressed[retroId]) hasInput = true
            }

            // Route analog stick axes (unmapped — sticks are not positional buttons).
            if (state.axes.size >= NUM_AXES) {
                val lx = applyDeadzone(state.axes[0])
                val ly = applyDeadzone(state.axes[1])
                val rx = applyDeadzone(state.axes[2])
                val ry = applyDeadzone(state.axes[3])

                desktopController.setAnalog(port, 0, 0, lx.toShort())
                desktopController.setAnalog(port, 0, 1, ly.toShort())
                desktopController.setAnalog(port, 1, 0, rx.toShort())
                desktopController.setAnalog(port, 1, 1, ry.toShort())

                if (lx != 0 || ly != 0 || rx != 0 || ry != 0) {
                    hasInput = true
                }
            }

            if (hasInput) {
                gamepadPortManager.reportActivity(port)
            }
        }

        // Publish the frame's right-stick deflection for UI viewport scroll (#1362).
        gamepadPortManager.setRightStickScroll(uiScrollY)

        // While a controller is under test, mask its test buttons (everything
        // except the D-pad) from UI navigation so e.g. the right face button
        // doesn't trigger "back" while the user is testing it (#1355/#1359). Only
        // the controller under test is masked — any other pad still navigates
        // normally — and the D-pad always navigates, so the user can move off the
        // tester.
        val testTarget = gamepadPortManager.testCaptureDeviceId.value
        val navStates = when {
            // During a hold-to-bind session every press (incl. D-pad) feeds the
            // binder, so mask ALL buttons on ALL controllers from navigation — a
            // press must never also move focus or trigger back (#1377).
            gamepadPortManager.bindCaptureActive.value -> Array(states.size) { idx ->
                val st = states[idx]
                st.copy(buttons = BooleanArray(st.buttons.size))
            }
            testTarget != null -> Array(states.size) { idx ->
                val st = states[idx]
                if (st.controllerId != testTarget) {
                    st
                } else {
                    val masked = BooleanArray(st.buttons.size) { i ->
                        st.buttons[i] && i in GamepadPosition.DPAD_UP.ordinal..GamepadPosition.DPAD_RIGHT.ordinal
                    }
                    st.copy(buttons = masked)
                }
            }
            else -> states
        }
        uiNavigator.handle(navStates)

        // Detect disconnections
        val disconnected = knownControllers - currentIds
        for (id in disconnected) {
            knownControllers.remove(id)
            gamepadPortManager.disconnectDevice(id)
        }
    }

    /**
     * Apply dead zone to an axis value. Returns 0 if within the dead zone,
     * otherwise returns the original value.
     */
    private fun applyDeadzone(value: Int): Int {
        return if (value in -AXIS_DEADZONE..AXIS_DEADZONE) 0 else value
    }
}
