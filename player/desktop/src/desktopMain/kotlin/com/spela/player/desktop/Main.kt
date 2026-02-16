package com.spela.player.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.spela.player.di.commonModule
import com.spela.player.di.platformModule
import com.spela.player.presentation.App
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import kotlinx.coroutines.delay
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

fun main(args: Array<String>) = application {
    val autoStartGameId = args.indexOf("--game").let { idx ->
        if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
    }

    startKoin {
        modules(commonModule, platformModule())
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Spela",
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
    ) {
        App()

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
