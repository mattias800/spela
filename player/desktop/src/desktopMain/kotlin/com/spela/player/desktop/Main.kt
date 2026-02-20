@file:Suppress("DEPRECATION")

package com.spela.player.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.spela.player.di.commonModule
import com.spela.player.di.platformModule
import com.spela.player.presentation.App
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.viewmodel.EmulationViewModel
import kotlinx.coroutines.delay
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

private val isMacOS = System.getProperty("os.name").orEmpty().contains("Mac", ignoreCase = true)

fun main(args: Array<String>) = application {
    val autoStartGameId = args.indexOf("--game").let { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }

    startKoin {
        modules(commonModule, platformModule())
    }

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
        onCloseRequest = ::exitApplication,
        title = windowTitle,
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
        icon = icon,
    ) {
        // macOS: transparent title bar blending with app background.
        // Uses official OpenJDK client properties (JDK 12+/17+).
        if (isMacOS) {
            LaunchedEffect(Unit) {
                val bg = java.awt.Color(10, 10, 16) // #0A0A10
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                window.background = bg
                window.rootPane.background = bg
                window.contentPane.background = bg
            }
        }

        Box(modifier = if (isMacOS) Modifier.padding(top = 28.dp) else Modifier) {
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
