package com.spela.player.libretro

import com.spela.player.domain.model.controllerStyleFromSdlType
import com.spela.player.presentation.navigation.NavigationEventBus
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.viewmodel.LibretroButtons
import com.spela.player.presentation.viewmodel.LibretroController
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

        /** Number of discrete buttons from SDL GameController. */
        private const val NUM_BUTTONS = 16

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

        for (state in states) {
            currentIds.add(state.controllerId)

            // Connect new devices
            if (state.controllerId !in knownControllers) {
                knownControllers.add(state.controllerId)
                gamepadPortManager.connectDevice(
                    state.controllerId,
                    state.name,
                    controllerStyleFromSdlType(state.type),
                )
            }

            val port = gamepadPortManager.getPort(state.controllerId)
            if (port < 0) continue

            var hasInput = false

            // Route button states
            for (buttonId in 0 until minOf(NUM_BUTTONS, state.buttons.size)) {
                desktopController.setButton(port, buttonId, state.buttons[buttonId])
                if (state.buttons[buttonId]) hasInput = true
            }

            // Route analog axes
            if (state.axes.size >= NUM_AXES) {
                val lx = applyDeadzone(state.axes[0])
                val ly = applyDeadzone(state.axes[1])
                val rx = applyDeadzone(state.axes[2])
                val ry = applyDeadzone(state.axes[3])
                val triggerL = state.axes[4]
                val triggerR = state.axes[5]

                desktopController.setAnalog(port, 0, 0, lx.toShort())
                desktopController.setAnalog(port, 0, 1, ly.toShort())
                desktopController.setAnalog(port, 1, 0, rx.toShort())
                desktopController.setAnalog(port, 1, 1, ry.toShort())

                // Digital trigger buttons (L2/R2)
                desktopController.setButton(port, LibretroButtons.L2, triggerL > TRIGGER_THRESHOLD)
                desktopController.setButton(port, LibretroButtons.R2, triggerR > TRIGGER_THRESHOLD)

                if (lx != 0 || ly != 0 || rx != 0 || ry != 0 ||
                    triggerL > TRIGGER_THRESHOLD || triggerR > TRIGGER_THRESHOLD
                ) {
                    hasInput = true
                }
            }

            if (hasInput) {
                gamepadPortManager.reportActivity(port)
            }
        }

        uiNavigator.handle(states)

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
