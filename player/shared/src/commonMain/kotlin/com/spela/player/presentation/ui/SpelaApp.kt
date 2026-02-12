package com.spela.player.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.screen.ConsoleScreen
import com.spela.player.presentation.ui.screen.ConsoleSettingsScreen
import com.spela.player.presentation.ui.screen.DownloadsScreen
import com.spela.player.presentation.ui.screen.GameDetailScreen
import com.spela.player.presentation.ui.screen.HomeScreen
import com.spela.player.presentation.ui.screen.InGameOverlay
import com.spela.player.presentation.ui.screen.LoginScreen
import com.spela.player.presentation.ui.screen.PlatformEmulationSurface
import com.spela.player.presentation.ui.screen.PlatformTouchControls

import com.spela.player.presentation.ui.screen.ServerConnectionScreen
import com.spela.player.presentation.ui.screen.SettingsScreen
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.gamepad.GamepadHandler
import com.spela.player.presentation.ui.theme.SpelaTheme
import com.spela.player.presentation.viewmodel.DownloadsViewModel
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.GameDetailViewModel
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.presentation.viewmodel.LoginViewModel
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.viewmodel.SettingsViewModel

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
    keyMappingViewModel: KeyMappingViewModel,
    secondaryDisplay: PlatformSecondaryDisplay,
) {
    SpelaTheme {
        val navState by navigationViewModel.state.collectAsState()

        // Show loading screen while session is being restored
        if (navState.isRestoringSession) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpColor.Background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SpColor.Primary)
            }
            return@SpelaTheme
        }

        val isGamepadScreen = navState.currentScreen !is SpScreen.ServerConnection &&
                navState.currentScreen !is SpScreen.Login

        // Handle system back button for non-emulation screens (Android)
        val hasBackStack = navState.currentScreen !is SpScreen.Home &&
                navState.currentScreen !is SpScreen.ServerConnection &&
                navState.currentScreen !is SpScreen.Login
        PlatformBackHandler(enabled = hasBackStack && !navState.showInGameOverlay) {
            navigationViewModel.onIntent(NavigationIntent.GoBack)
        }

        GamepadHandler(
            onBack = if (isGamepadScreen) {
                { navigationViewModel.onIntent(NavigationIntent.GoBack) }
            } else null,
        ) {
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
                            if (navState.isGoingBack) {
                                (slideInHorizontally { -it / 3 } + fadeIn())
                                    .togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
                            } else {
                                (slideInHorizontally { it / 3 } + fadeIn())
                                    .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                            }
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
                                val serverUrl = serverConnectionViewModel.state.value
                                    .servers.firstOrNull { it.id == serverConnectionViewModel.state.value.selectedServerId }
                                    ?.url
                                    ?: navState.restoredServerUrl
                                    ?: ""
                                LoginScreen(
                                    viewModel = loginViewModel,
                                    serverUrl = serverUrl,
                                    onLoginSuccess = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Home)
                                        )
                                    },
                                    onChangeServer = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ServerConnection)
                                        )
                                    },
                                )
                            }

                            is SpScreen.Home -> {
                                val downloadsState by downloadsViewModel.state.collectAsState()
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
                                    onNavigateToDownloads = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Downloads)
                                        )
                                    },
                                    onNavigateToSettings = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Settings)
                                        )
                                    },
                                    hasActiveDownloads = downloadsState.activeDownloads.isNotEmpty(),
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
                                    onNavigateToConsoleSettings = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ConsoleSettings(screen.consoleId))
                                        )
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
                                DownloadsScreen(
                                    viewModel = downloadsViewModel,
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.Settings -> {
                                SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                    onLogout = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ServerConnection)
                                        )
                                    },
                                    onNavigateToConsoleSettings = { consoleId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ConsoleSettings(consoleId))
                                        )
                                    },
                                )
                            }

                            is SpScreen.ConsoleSettings -> {
                                ConsoleSettingsScreen(
                                    consoleId = screen.consoleId,
                                    settingsViewModel = settingsViewModel,
                                    keyMappingViewModel = keyMappingViewModel,
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
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

                        val emulationState by emulationViewModel.state.collectAsState()
                        val secondaryAvailable by secondaryDisplay.isAvailable.collectAsState()

                        // Notify ViewModel about secondary display availability changes
                        LaunchedEffect(secondaryAvailable) {
                            emulationViewModel.onIntent(
                                EmulationIntent.SecondaryDisplayAvailabilityChanged(secondaryAvailable)
                            )
                        }

                        // Handle auto-exit (when auto-save skips confirmation)
                        LaunchedEffect(emulationState.requestExit) {
                            if (emulationState.requestExit) {
                                emulationViewModel.onIntent(EmulationIntent.ClearExitRequest)
                                navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                            }
                        }

                        // Emulation video surface (renders behind the overlay)
                        PlatformEmulationSurface(
                            controller = libretroController,
                            selectedShader = emulationState.selectedShader,
                            onEscapePressed = {
                                emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
                            },
                        )

                        // Error overlay: shown when emulation fails to start
                        if (emulationState.error != null) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(SpSpacing.XLarge),
                                ) {
                                    Text(
                                        text = emulationState.error ?: "",
                                        style = SpTypography.BodyMedium,
                                        color = SpColor.Error,
                                    )
                                    Spacer(Modifier.height(SpSpacing.Large))
                                    SpButton(
                                        text = "Exit",
                                        onClick = {
                                            emulationViewModel.onIntent(EmulationIntent.StopGame)
                                            navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                                        },
                                    )
                                }
                            }
                        }

                        // Loading spinner over black background while preparing game
                        if (emulationState.isLoading && emulationState.error == null) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }

                        // Touch gamepad controls (Android only, no-op on desktop)
                        // Hidden when secondary display is active (controls move there)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = emulationState.isRunning && !emulationState.showOverlay && !emulationState.secondaryDisplayActive,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            PlatformTouchControls(
                                controller = libretroController,
                            )
                        }

                        InGameOverlay(
                            viewModel = emulationViewModel,
                            onExit = {
                                navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                            },
                        )
                    }
                }

            }
        }
        }
    }
}
