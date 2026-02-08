package com.spela.player.presentation

import androidx.compose.runtime.Composable
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.ui.SpelaApp
import com.spela.player.presentation.viewmodel.DownloadsViewModel
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.GameDetailViewModel
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.LoginViewModel
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel
import com.spela.player.presentation.viewmodel.SettingsViewModel
import org.koin.compose.koinInject

/**
 * Root composable for the Spela player app.
 * Delegates to [SpelaApp] which handles full navigation, theming, and all screens.
 */
@Composable
fun App() {
    val navigationViewModel: NavigationViewModel = koinInject()
    val serverConnectionViewModel: ServerConnectionViewModel = koinInject()
    val loginViewModel: LoginViewModel = koinInject()
    val gameListViewModel: GameListViewModel = koinInject()
    val gameDetailViewModel: GameDetailViewModel = koinInject()
    val emulationViewModel: EmulationViewModel = koinInject()
    val downloadsViewModel: DownloadsViewModel = koinInject()
    val settingsViewModel: SettingsViewModel = koinInject()

    SpelaApp(
        navigationViewModel = navigationViewModel,
        serverConnectionViewModel = serverConnectionViewModel,
        loginViewModel = loginViewModel,
        gameListViewModel = gameListViewModel,
        gameDetailViewModel = gameDetailViewModel,
        emulationViewModel = emulationViewModel,
        downloadsViewModel = downloadsViewModel,
        settingsViewModel = settingsViewModel,
    )
}
