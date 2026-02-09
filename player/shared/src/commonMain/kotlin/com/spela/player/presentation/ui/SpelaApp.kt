package com.spela.player.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpBottomBar
import com.spela.player.presentation.ui.components.SpBottomBarItem
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.screen.ConsoleScreen
import com.spela.player.presentation.ui.screen.DownloadsScreen
import com.spela.player.presentation.ui.screen.GameDetailScreen
import com.spela.player.presentation.ui.screen.HomeScreen
import com.spela.player.presentation.ui.screen.InGameOverlay
import com.spela.player.presentation.ui.screen.LoginScreen
import com.spela.player.presentation.ui.screen.PlatformEmulationSurface
import com.spela.player.presentation.ui.screen.ServerConnectionScreen
import com.spela.player.presentation.ui.screen.SettingsScreen
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpelaTheme
import com.spela.player.presentation.viewmodel.DownloadsViewModel
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.GameDetailViewModel
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.presentation.viewmodel.LoginViewModel
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel
import com.spela.player.presentation.viewmodel.SettingsViewModel

private val bottomBarItems = listOf(
    SpBottomBarItem(label = "Home", icon = "\uD83C\uDFE0", route = "home"),
    SpBottomBarItem(label = "Downloads", icon = "\u2B07", route = "downloads"),
    SpBottomBarItem(label = "Settings", icon = "\u2699", route = "settings"),
)

@Composable
fun SpelaApp(
    navigationViewModel: NavigationViewModel,
    serverConnectionViewModel: ServerConnectionViewModel,
    loginViewModel: LoginViewModel,
    gameListViewModel: GameListViewModel,
    gameDetailViewModel: GameDetailViewModel,
    emulationViewModel: EmulationViewModel,
    libretroController: LibretroController,
    downloadsViewModel: DownloadsViewModel,
    settingsViewModel: SettingsViewModel,
) {
    SpelaTheme {
        val navState by navigationViewModel.state.collectAsState()
        val showBottomBar = navState.currentScreen is SpScreen.Home ||
                navState.currentScreen is SpScreen.Downloads ||
                navState.currentScreen is SpScreen.Settings

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = navState.currentScreen,
                        transitionSpec = {
                            (slideInHorizontally { it / 3 } + fadeIn())
                                .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                        },
                    ) { screen ->
                        when (screen) {
                            is SpScreen.ServerConnection -> {
                                ServerConnectionScreen(
                                    viewModel = serverConnectionViewModel,
                                    onServerSelected = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Login)
                                        )
                                    },
                                )
                            }

                            is SpScreen.Login -> {
                                LoginScreen(
                                    viewModel = loginViewModel,
                                    serverUrl = serverConnectionViewModel.state.value
                                        .servers.firstOrNull { it.id == serverConnectionViewModel.state.value.selectedServerId }
                                        ?.url ?: "",
                                    onLoginSuccess = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Home)
                                        )
                                    },
                                )
                            }

                            is SpScreen.Home -> {
                                HomeScreen(
                                    viewModel = gameListViewModel,
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                    onConsoleSelected = { consoleId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Console(consoleId))
                                        )
                                    },
                                )
                            }

                            is SpScreen.Console -> {
                                ConsoleScreen(
                                    consoleId = screen.consoleId,
                                    viewModel = gameListViewModel,
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.GameDetail -> {
                                GameDetailScreen(
                                    gameId = screen.gameId,
                                    viewModel = gameDetailViewModel,
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                    onPlay = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.ShowOverlay(gameId)
                                        )
                                    },
                                )
                            }

                            is SpScreen.Downloads -> {
                                DownloadsScreen(viewModel = downloadsViewModel)
                            }

                            is SpScreen.Settings -> {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onLogout = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ServerConnection)
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Emulation surface + in-game overlay
                    if (navState.showInGameOverlay) {
                        LaunchedEffect(navState.overlayGameId) {
                            navState.overlayGameId?.let { gameId ->
                                emulationViewModel.onIntent(
                                    EmulationIntent.StartGame(gameId)
                                )
                            }
                        }

                        // Intercept back button during emulation: toggle overlay
                        PlatformBackHandler {
                            emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
                        }

                        // Emulation video surface (renders behind the overlay)
                        PlatformEmulationSurface(
                            controller = libretroController,
                        )

                        // Loading spinner over black background while preparing game
                        val emulationState by emulationViewModel.state.collectAsState()
                        if (emulationState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }

                        InGameOverlay(
                            viewModel = emulationViewModel,
                            onExit = {
                                navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                            },
                        )
                    }
                }

                // Bottom navigation bar
                if (showBottomBar) {
                    SpBottomBar(
                        items = bottomBarItems,
                        selectedRoute = when (navState.currentScreen) {
                            is SpScreen.Home -> "home"
                            is SpScreen.Downloads -> "downloads"
                            is SpScreen.Settings -> "settings"
                            else -> "home"
                        },
                        onItemSelected = { route ->
                            navigationViewModel.onIntent(NavigationIntent.SwitchTab(route))
                        },
                    )
                }
            }
        }
    }
}
