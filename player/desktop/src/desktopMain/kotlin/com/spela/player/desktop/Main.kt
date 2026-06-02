@file:Suppress("DEPRECATION")
@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.spela.player.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.spela.player.di.commonModule
import com.spela.player.di.platformModule
import com.spela.player.libretro.DesktopGamepadPoller
import com.spela.player.libretro.DesktopGamepadSynth
import com.spela.player.presentation.ui.gamepad.InputModeClassifier
import com.spela.player.presentation.App
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.viewmodel.EmulationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

private val isMacOS = System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)
private val isWindows = System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)
private val isLinux = System.getProperty("os.name").orEmpty().contains("Linux", ignoreCase = true)

fun main(args: Array<String>) {
    val autoStartGameId = args.indexOf("--game").let { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }

    startKoin {
        modules(commonModule, platformModule())
    }

    // Attribute synthesized gamepad key events to the gamepad (not the keyboard)
    // so the control method in use drives the nav style. See DesktopGamepadSynth.
    InputModeClassifier.isKeyboardNavFromGamepad = { DesktopGamepadSynth.wasRecent() }

    // Start gamepad poller (SDL2)
    val gamepadPoller = getKoin().get<DesktopGamepadPoller>()
    val gamepadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    gamepadPoller.start(gamepadScope)

    application {

    val emulationViewModel = getKoin().get<EmulationViewModel>()
    val emulationState by emulationViewModel.state.collectAsState()

    val windowTitle = when {
        emulationState.isChallengeMode && emulationState.gameTitle.isNotBlank() ->
            "Spela \u2014 Challenge: ${emulationState.gameTitle}"
        emulationState.isRunning && emulationState.gameTitle.isNotBlank() ->
            "Spela \u2014 ${emulationState.gameTitle}"
        else -> "Spela"
    }

    val icon = useResource("spela-icon.svg") { loadSvgPainter(it, Density(1f)) }

    val windowState = rememberWindowState(width = 1280.dp, height = 720.dp)

    Window(
        onCloseRequest = {
            gamepadPoller.stop()
            exitApplication()
        },
        title = windowTitle,
        state = windowState,
        icon = icon,
    ) {
        // Pause the game (and play-time accrual) when the window is minimized
        // or loses focus, and resume when it comes back — mirroring Android's
        // onPause/onResume. Without this, a game left in the background keeps
        // running and counting play time (#1282).
        //
        // Must live INSIDE the Window content: LocalWindowInfo is provided
        // per-window — reading it at application{} scope throws
        // "CompositionLocal LocalWindowInfo not present" on startup.
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(Unit) {
            snapshotFlow { windowInfo.isWindowFocused && !windowState.isMinimized }
                .collect { active ->
                    val st = emulationViewModel.state.value
                    if (!active && st.isRunning && !st.isPaused) {
                        println("[PlayTime] window background → pause (game=${st.gameId})")
                        emulationViewModel.onIntent(EmulationIntent.LifecyclePause)
                    } else if (active && st.isLifecyclePaused) {
                        println("[PlayTime] window foreground → resume (game=${st.gameId})")
                        emulationViewModel.onIntent(EmulationIntent.LifecycleResume)
                    }
                }
        }
        // macOS: transparent title bar that lets Compose content show through.
        // Uses official OpenJDK client properties (JDK 12+/17+).
        // fullWindowContent extends Compose rendering behind the title bar.
        // transparentTitleBar makes the native title bar transparent.
        // The AWT background is an opaque fallback shown briefly before Compose renders.
        if (isMacOS) {
            LaunchedEffect(Unit) {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                val bg = java.awt.Color(10, 10, 16) // #0A0A10 — fallback before Compose renders
                window.background = bg
                window.rootPane.background = bg
                window.contentPane.background = bg
            }
        }

        // Windows / Linux: transparent title bar matching the macOS appearance.
        // Uses JetBrains Runtime (JBR) custom title bar API when available
        // (always present in packaged distributions). Gracefully degrades
        // to standard title bar on other JDKs (dev mode).
        if (isWindows || isLinux) {
            LaunchedEffect(Unit) {
                applyJbrTransparentTitleBar(window)
            }
        }

        // Provide the title bar inset so SpTopBar and floating-bar screens
        // can offset their content below the native window controls.
        // The app's background/gradient extends to the top of the window, showing
        // through the transparent title bar.
        val titleBarInset = when {
            isMacOS -> 28.dp
            (isWindows || isLinux) && isJbrTitleBarActive -> 32.dp
            else -> 0.dp
        }

        // Linux fractional HiDPI: AWT's device scale is integer-only on X11, so
        // on a 1.5x-scaled desktop Compose inherits density 1.0 and renders too
        // small. Compensate by overriding LocalDensity with the scale published
        // over XSETTINGS. See LinuxDisplayScale.kt.
        val densityOverride = remember {
            if (isLinux) detectLinuxDensityOverride(window) else null
        }
        val density = densityOverride?.let { Density(it) } ?: LocalDensity.current

        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalTitleBarInset provides titleBarInset,
                LocalDensity provides density,
            ) {
                App()
            }

            // Windows: the JBR custom title bar reserves a transparent top strip,
            // but Compose's heavyweight surface covers it, so by default the OS
            // hit-tests the whole strip as client area and the window can't be
            // dragged (#1228). Overlay an invisible strip that, on each pointer
            // event, calls forceHitTest(false) to mark the region as caption —
            // Windows then handles a press there as a window drag (and the native
            // min/max/close buttons keep working, since they're caption too).
            // (Linux/macOS title bars remain natively draggable — Windows only.)
            if (isWindows && isJbrTitleBarActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(titleBarInset)
                        // forceHitTest must run synchronously during event
                        // dispatch (onPointerEvent), not from a pointerInput
                        // coroutine which fires too late for the OS hit test.
                        .onPointerEvent(PointerEventType.Move) { jbrMarkTitleBarDraggable() }
                        .onPointerEvent(PointerEventType.Enter) { jbrMarkTitleBarDraggable() }
                        .onPointerEvent(PointerEventType.Press) { jbrMarkTitleBarDraggable() },
                )
            }
        }

        // Auto-start a game when --game <gameId> is passed on the command line.
        // Waits for the app to initialize and connect to the server first.
        if (autoStartGameId != null) {
            LaunchedEffect(autoStartGameId) {
                delay(3000)
                println("[AutoStart] Launching game: $autoStartGameId")
                val navigationViewModel = getKoin().get<NavigationViewModel>()
                navigationViewModel.onIntent(NavigationIntent.ShowOverlay(autoStartGameId))
            }
        }
    }
    }
}

/** Track whether JBR title bar was successfully applied. Backed by Compose
 *  state so the (async) activation in applyJbrTransparentTitleBar triggers
 *  recomposition — otherwise the drag strip / inset never re-evaluate. */
private val isJbrTitleBarActiveState = androidx.compose.runtime.mutableStateOf(false)
private var isJbrTitleBarActive: Boolean
    get() = isJbrTitleBarActiveState.value
    set(value) { isJbrTitleBarActiveState.value = value }

/** The JBR CustomTitleBar instance + its forceHitTest method, captured so the
 *  Compose drag strip can mark the top region as draggable (#1228). */
private var jbrTitleBar: Any? = null
private var jbrForceHitTestMethod: java.lang.reflect.Method? = null

/**
 * Mark the current pointer position in the title-bar strip as non-client
 * (caption), so Windows treats a press there as a window drag / native caption
 * button rather than passing it to the (opaque) Compose surface. Must be called
 * on each pointer event over the strip. No-op off JBR.
 */
private fun jbrMarkTitleBarDraggable() {
    val tb = jbrTitleBar ?: return
    val m = jbrForceHitTestMethod ?: return
    try {
        m.invoke(tb, false)
    } catch (_: Throwable) {
    }
}

/**
 * Apply transparent title bar via JetBrains Runtime (JBR) custom title bar API.
 * Works on Windows and Linux when the app is packaged with JBR (MSI, DEB, AppImage, Flatpak).
 * Gracefully degrades to standard title bar on non-JBR JDKs (dev mode).
 */
private fun applyJbrTransparentTitleBar(window: java.awt.Window) {
    try {
        // JBR custom title bar API, called reflectively so non-JBR JDKs degrade
        // gracefully (getWindowDecorations() is absent/returns null off JBR).
        val jbr = Class.forName("com.jetbrains.JBR")
        val windowDecorations = jbr.getMethod("getWindowDecorations").invoke(null)
        if (windowDecorations == null) {
            println("[TitleBar] JBR WindowDecorations unavailable — standard title bar")
            return
        }
        // Invoke via the PUBLIC interfaces — the runtime impl class is a synthetic
        // module-private type and reflecting on it throws IllegalAccessException.
        val wdInterface = Class.forName("com.jetbrains.WindowDecorations")
        val ctbInterface = Class.forName("com.jetbrains.WindowDecorations\$CustomTitleBar")
        val titleBar = wdInterface.getMethod("createCustomTitleBar").invoke(windowDecorations)
        ctbInterface.getMethod("setHeight", java.lang.Float.TYPE).invoke(titleBar, 32f)
        // Capture for the Compose drag strip (#1228). forceHitTest(false) marks a
        // point as non-client (caption) so the OS handles drag / native buttons.
        jbrTitleBar = titleBar
        jbrForceHitTestMethod = try {
            ctbInterface.getMethod("forceHitTest", java.lang.Boolean.TYPE)
        } catch (e: NoSuchMethodException) {
            println("[TitleBar] forceHitTest not found; methods=" +
                ctbInterface.methods.joinToString { it.name })
            null
        }
        // setCustomTitleBar is overloaded ((Frame, ..), (Dialog, ..)) and the param
        // types vary by JBR version, so match the overload to the actual arg types.
        val setMethod = wdInterface.methods.firstOrNull { m ->
            m.name == "setCustomTitleBar" && m.parameterCount == 2 &&
                m.parameterTypes[0].isInstance(window) && m.parameterTypes[1].isInstance(titleBar)
        } ?: run {
            println("[TitleBar] no matching setCustomTitleBar overload — standard title bar")
            return
        }
        setMethod.invoke(windowDecorations, window, titleBar)

        val bg = java.awt.Color(10, 10, 16) // #0A0A10 — fallback behind the transparent bar
        window.background = bg
        (window as? javax.swing.JFrame)?.let {
            it.rootPane.background = bg
            it.contentPane.background = bg
        }
        isJbrTitleBarActive = true
    } catch (e: Throwable) {
        println("[TitleBar] JBR custom title bar failed: $e")
    }
}
