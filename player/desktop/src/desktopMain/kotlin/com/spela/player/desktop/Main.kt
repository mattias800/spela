@file:Suppress("DEPRECATION")

package com.spela.player.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.spela.player.presentation.App
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
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

fun main(args: Array<String>) {
    val autoStartGameId = args.indexOf("--game").let { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }

    startKoin {
        modules(commonModule, platformModule())
    }

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

    Window(
        onCloseRequest = {
            gamepadPoller.stop()
            exitApplication()
        },
        title = windowTitle,
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
        icon = icon,
    ) {
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

        // On macOS, provide the title bar inset so SpTopBar and floating-bar screens
        // can offset their content below the native traffic light buttons.
        // The app's background/gradient extends to the top of the window, showing
        // through the transparent title bar.
        CompositionLocalProvider(LocalTitleBarInset provides if (isMacOS) 28.dp else 0.dp) {
            App()
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
