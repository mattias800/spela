package com.spela.player.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.spela.player.di.commonModule
import com.spela.player.di.platformModule
import com.spela.player.presentation.App
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(commonModule, platformModule())
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Spela",
        state = rememberWindowState(width = 1280.dp, height = 720.dp),
    ) {
        App()
    }
}
