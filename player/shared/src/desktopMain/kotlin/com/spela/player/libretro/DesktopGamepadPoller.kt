package com.spela.player.libretro

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

internal class CalibrationInputMask {
    private val maskedControllers = mutableSetOf<Int>()

    fun update(
        controllerId: Int,
        rawPressedPositions: Set<GamepadPosition>,
        consumedByCalibration: Boolean,
    ): Boolean {
        if (consumedByCalibration && rawPressedPositions.isNotEmpty()) {
            maskedControllers += controllerId
        }
        if (rawPressedPositions.isEmpty()) {
            maskedControllers -= controllerId
        }
        return controllerId in maskedControllers
    }

    fun isMasked(controllerId: Int): Boolean = controllerId in maskedControllers

    fun clear(controllerId: Int) {
        maskedControllers -= controllerId
    }

    fun clear() {
        maskedControllers.clear()
    }
}

/**
 * Polls connected gamepads via SDL3 (JNI) and routes their input
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
    /** Confirm/back convention (#1448): true = Nintendo (EAST confirms). */
    private val confirmIsEast: () -> Boolean = { false },
    /**
     * Invoked when the [OverlayHotkey] combo is held in-game (#1682) — the only
     * gamepad route into the in-game overlay on desktop, where there is no
     * system back button and Escape needs a keyboard.
     */
    private val onOverlayHotkey: () -> Unit = {},
) {
    companion object {
        private const val POLL_INTERVAL_MS = 8L // ~120 Hz

        /** Axis dead zone (SDL range is -32768..32767, ~15% dead zone). */
        private const val AXIS_DEADZONE = 4800

        /** SDL axis magnitude, for normalizing to -1..1 (#1362 right-stick scroll). */
        private const val AXIS_RANGE = 32768f

        /** Libretro analog button pressure range (0..0x7FFF). */
        internal const val ANALOG_BUTTON_RANGE = 32767

        /** Number of axes: LX, LY, RX, RY, TriggerL, TriggerR. */
        private const val NUM_AXES = 6

        /** Trigger threshold to consider as digital press. */
        private const val TRIGGER_THRESHOLD = 8000

        internal fun normalizeTriggerPressure(value: Int): Short {
            return value.coerceIn(0, ANALOG_BUTTON_RANGE).toShort()
        }

        internal data class TriggerAxes(
            val analogPressures: Map<Int, Short>,
            val l2Pressed: Boolean,
            val r2Pressed: Boolean,
        )

        internal fun resolveTriggerAxes(
            l2Raw: Int,
            r2Raw: Int,
            mapping: Map<GamepadPosition, Int>,
        ): TriggerAxes {
            val l2 = normalizeTriggerPressure(l2Raw)
            val r2 = normalizeTriggerPressure(r2Raw)
            return TriggerAxes(
                analogPressures = GamepadButtonResolver.resolveAnalogTriggerPressures(l2, r2, mapping),
                l2Pressed = l2Raw > TRIGGER_THRESHOLD,
                r2Pressed = r2Raw > TRIGGER_THRESHOLD,
            )
        }

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
        confirmIsEast = confirmIsEast,
    )

    private val analogTriggerRouteTracker = AnalogTriggerRouteTracker(GamepadPortManager.MAX_PORTS)
    private val routedPortsByController = mutableMapOf<Int, Int>()
    private val calibrationInputMask = CalibrationInputMask()

    /** Per-controller Select+Start hold state for the overlay hotkey (#1682). */
    private val overlayHotkeyDetectors = mutableMapOf<Int, OverlayHotkeyDetector>()

    fun start(scope: CoroutineScope) {
        if (pollJob != null) return

        pollJob = scope.launch(pollDispatcher) {
            initialized = jni.nativeGamepadInit()
            if (!initialized) {
                println("[GamepadPoller] SDL3 gamepad init failed (see [GamepadSDL] log lines)")
                return@launch
            }
            println("[GamepadPoller] SDL3 gamepad initialized")

            while (isActive) {
                poll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
        (controller as? DesktopLibretroController)?.let { desktopController ->
            routedPortsByController.values.toSet().forEach { port ->
                clearAnalogTriggerPort(desktopController, port)
            }
        }
        routedPortsByController.clear()
        calibrationInputMask.clear()
        overlayHotkeyDetectors.clear()
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

        // The confirm button under the current convention (#1448) — the tester's
        // toggle, exempt from capture/masking so the user can turn it off.
        val confirmPos = if (confirmIsEast()) GamepadPosition.EAST else GamepadPosition.SOUTH

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
            val rawPressedPositions = GamepadPosition.entries.filterTo(mutableSetOf()) {
                positionPressed[it.ordinal]
            }

            // Input-layer calibration capture (#1341): while the prompt is active,
            // raw positions feed calibration and are masked from both gameplay and
            // UI navigation.
            val isCalibrationCapturing =
                gamepadPortManager.reportInputCalibrationPressedPositions(state.controllerId, rawPressedPositions)
            val isCalibrationMasked = calibrationInputMask.update(
                state.controllerId,
                rawPressedPositions,
                isCalibrationCapturing,
            )

            // Live input tester (#1355/#1359/#1448): only for the controller
            // currently under test, report every pressed position — INCLUDING the
            // D-pad and the confirm button — plus analog stick deflection, so the
            // whole controller lights up and is testable. The confirm button is ALSO
            // reported as a held signal that drives the hold-to-stop timer (and it's
            // masked from navigation below so a single press no longer exits). Done
            // before the port check so an *unassigned* (cleared) controller is still
            // fully testable.
            if (gamepadPortManager.testCaptureDeviceId.value == state.controllerId) {
                gamepadPortManager.reportPressedPositions(
                    state.controllerId,
                    rawPressedPositions,
                )
                gamepadPortManager.reportTestConfirmHeld(state.controllerId, positionPressed[confirmPos.ordinal])
                if (state.axes.size >= NUM_AXES) {
                    gamepadPortManager.reportTestSticks(
                        state.controllerId,
                        applyDeadzone(state.axes[0]) / AXIS_RANGE,
                        applyDeadzone(state.axes[1]) / AXIS_RANGE,
                        applyDeadzone(state.axes[2]) / AXIS_RANGE,
                        applyDeadzone(state.axes[3]) / AXIS_RANGE,
                    )
                }
            }

            // Hold-to-bind capture (#1377): any controller, INCLUDING the D-pad, so
            // the user can rebind D-pad directions. Reported for every controller —
            // the editor binds with whichever pad the user holds a button on.
            if (gamepadPortManager.bindCaptureActive.value) {
                gamepadPortManager.reportBindPressedPositions(
                    state.controllerId,
                    rawPressedPositions,
                )
            }

            // Right-stick viewport scroll (#1362): track the largest right-stick Y
            // deflection across pads (before the port check, so an unassigned pad
            // still scrolls the UI). The UI's RightStickScroll effect consumes it.
            // The controller under test is skipped (#1448): its right stick feeds the
            // tester's stick indicator, not the scroller.
            if (state.axes.size >= NUM_AXES &&
                gamepadPortManager.testCaptureDeviceId.value != state.controllerId
            ) {
                val ry = applyDeadzone(state.axes[3]) / AXIS_RANGE
                if (abs(ry) > abs(uiScrollY)) uiScrollY = ry
            }

            // Emulation routing requires an assigned player port; unassigned
            // controllers stop here (they only feed the tester above).
            val port = gamepadPortManager.getPort(state.controllerId)
            val previousPort = routedPortsByController[state.controllerId]
            if (previousPort != port) {
                if (previousPort != null) clearAnalogTriggerPort(desktopController, previousPort)
                if (port >= 0) clearAnalogTriggerPort(desktopController, port)
            }
            if (port < 0) {
                routedPortsByController.remove(state.controllerId)
                continue
            }
            if (isCalibrationCapturing || isCalibrationMasked) continue
            routedPortsByController[state.controllerId] = port

            // Overlay hotkey (#1682): Select+Start held together opens the
            // in-game overlay. Tracked even when not in-game so the latch (and
            // therefore the mask below) stays valid for as long as the buttons
            // are held — including after the overlay has opened, which is what
            // keeps the core from resuming to a stuck Select+Start.
            val hotkey = overlayHotkeyDetectors.getOrPut(state.controllerId) { OverlayHotkeyDetector() }
            if (hotkey.update(System.currentTimeMillis(), OverlayHotkey.isCombo(rawPressedPositions)) &&
                isInGame()
            ) {
                onOverlayHotkey()
            }
            if (hotkey.isLatched) {
                OverlayHotkey.POSITIONS.forEach { positionPressed[it.ordinal] = false }
            }

            var hasInput = false

            // Apply the configurable mapping layer (position -> RetroPad), with
            // fan-in handled so a released position can't clobber another's press.
            val mapping = gamepadPortManager.getCalibratedGamepadMapping(port, state.controllerId)
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
                val triggerAxes = resolveTriggerAxes(state.axes[4], state.axes[5], mapping)

                desktopController.setAnalog(port, 0, 0, lx.toShort())
                desktopController.setAnalog(port, 0, 1, ly.toShort())
                desktopController.setAnalog(port, 1, 0, rx.toShort())
                desktopController.setAnalog(port, 1, 1, ry.toShort())
                clearStaleAnalogTriggerIds(desktopController, port, triggerAxes.analogPressures.keys)
                triggerAxes.analogPressures.forEach { (retroId, pressure) ->
                    desktopController.setAnalogButton(port, retroId, pressure)
                }

                if (lx != 0 || ly != 0 || rx != 0 || ry != 0 ||
                    triggerAxes.analogPressures.any { it.value.toInt() > 0 }
                ) {
                    hasInput = true
                }
            } else {
                clearAnalogTriggerPort(desktopController, port)
            }

            if (hasInput) {
                gamepadPortManager.reportActivity(port)
            }
        }

        // Publish the frame's right-stick deflection for UI viewport scroll (#1362).
        gamepadPortManager.setRightStickScroll(uiScrollY)

        // While a controller is under test, mask ALL of its buttons from UI
        // navigation (#1355/#1359/#1448): the tester captures everything — including
        // the D-pad and the confirm button — so nothing navigates or triggers
        // "back". The confirm button drives the hold-to-stop timer instead of
        // navigating; deactivation happens in the tester on release after a full
        // hold, so the confirm press never reaches navigation. Activation is
        // unaffected: the mask only applies once the tester is already active (a
        // confirm press to activate happens while testTarget is still null). Only
        // the controller under test is masked; any other pad still navigates.
        val testTarget = gamepadPortManager.testCaptureDeviceId.value
        val calibrationTarget = gamepadPortManager.inputCalibrationCapture.value?.deviceId
        val navStates = when {
            // During a hold-to-bind session every press (incl. D-pad) feeds the
            // binder, so mask ALL buttons on ALL controllers from navigation — a
            // press must never also move focus or trigger back (#1377).
            gamepadPortManager.bindCaptureActive.value -> Array(states.size) { idx ->
                val st = states[idx]
                st.copy(buttons = BooleanArray(st.buttons.size))
            }
            else -> Array(states.size) { idx ->
                val st = states[idx]
                val shouldMask = st.controllerId == testTarget ||
                    st.controllerId == calibrationTarget ||
                    calibrationInputMask.isMasked(st.controllerId)
                if (shouldMask) st.copy(buttons = BooleanArray(st.buttons.size)) else st
            }
        }
        uiNavigator.handle(navStates)

        // Detect disconnections
        val disconnected = knownControllers - currentIds
        for (id in disconnected) {
            val port = routedPortsByController.remove(id) ?: gamepadPortManager.getPort(id)
            if (port >= 0) {
                clearAnalogTriggerPort(desktopController, port)
            }
            knownControllers.remove(id)
            calibrationInputMask.clear(id)
            overlayHotkeyDetectors.remove(id)
            gamepadPortManager.disconnectDevice(id)
        }
    }

    private fun clearStaleAnalogTriggerIds(
        desktopController: DesktopLibretroController,
        port: Int,
        currentIds: Set<Int>,
    ) {
        analogTriggerRouteTracker.update(port, currentIds).forEach { staleId ->
            desktopController.clearAnalogButton(port, staleId)
            desktopController.setButton(port, staleId, false)
        }
    }

    private fun clearAnalogTriggerPort(desktopController: DesktopLibretroController, port: Int) {
        for (retroId in 0 until GamepadButtonResolver.RETRO_BUTTON_COUNT) {
            desktopController.clearAnalogButton(port, retroId)
        }
        analogTriggerRouteTracker.clearPort(port).forEach { staleId ->
            desktopController.setButton(port, staleId, false)
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
