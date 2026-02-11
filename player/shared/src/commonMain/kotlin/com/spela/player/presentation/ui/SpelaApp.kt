package com.spela.player.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpButton
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
import com.spela.player.presentation.ui.screen.PlatformTouchControls
import com.spela.player.presentation.ui.screen.platformControlHintText
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
        val tabRoutes = listOf("home", "downloads", "settings")
        val currentTabIndex = when (navState.currentScreen) {
            is SpScreen.Home -> 0
            is SpScreen.Downloads -> 1
            is SpScreen.Settings -> 2
            else -> -1
        }
        val onTabSwitch: ((Int) -> Unit)? = if (currentTabIndex >= 0) { direction ->
            val newIndex = (currentTabIndex + direction).coerceIn(0, tabRoutes.lastIndex)
            if (newIndex != currentTabIndex) {
                navigationViewModel.onIntent(NavigationIntent.SwitchTab(tabRoutes[newIndex]))
            }
        } else null

        GamepadHandler(
            onBack = if (isGamepadScreen) {
                { navigationViewModel.onIntent(NavigationIntent.GoBack) }
            } else null,
            onTabSwitch = onTabSwitch,
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

                        val emulationState by emulationViewModel.state.collectAsState()

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
                        if (emulationState.isRunning && !emulationState.showOverlay) {
                            PlatformTouchControls(
                                controller = libretroController,
                            )
                        }

                        // First-time control hint overlay
                        if (emulationState.isRunning && !emulationState.showOverlay && emulationState.showControlHint) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(SpColor.Scrim)
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            emulationViewModel.onIntent(EmulationIntent.DismissControlHint)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .padding(SpSpacing.XLarge)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(SpColor.SurfaceElevated)
                                        .padding(SpSpacing.XLarge),
                                ) {
                                    Text(
                                        text = platformControlHintText(),
                                        style = SpTypography.BodyMedium,
                                        color = SpColor.OnBackground,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(SpSpacing.Large))
                                    Text(
                                        text = "Tap anywhere to start playing",
                                        style = SpTypography.LabelSmall,
                                        color = SpColor.OnBackgroundTertiary,
                                    )
                                }
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
}
