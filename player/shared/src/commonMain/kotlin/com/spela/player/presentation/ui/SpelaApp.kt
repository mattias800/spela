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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.domain.model.NetplaySessionStatus
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.intent.NetplayIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.components.BottomNavTab
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpBottomNavBar
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpOfflineBanner
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.presentation.ui.feature.ingame.DsPrimaryTouchOverlay
import com.spela.player.presentation.ui.screen.ConsoleScreen
import com.spela.player.presentation.ui.screen.ConsoleSettingsScreen
import com.spela.player.presentation.ui.screen.DownloadsScreen
import com.spela.player.presentation.ui.screen.GameDetailScreen
import com.spela.player.presentation.ui.screen.HomeScreen
import com.spela.player.presentation.ui.screen.InGameOverlay
import com.spela.player.presentation.ui.screen.LoginScreen
import com.spela.player.presentation.ui.feature.ingame.PlatformEmulationSurface
import com.spela.player.presentation.ui.feature.ingame.PlatformTouchControls

import com.spela.player.presentation.ui.screen.NetplayListScreen
import com.spela.player.presentation.ui.screen.NetplayLobbyScreen
import com.spela.player.presentation.ui.screen.NetplayStartConfig
import com.spela.player.presentation.ui.screen.RelayDetailScreen
import com.spela.player.presentation.ui.screen.RelaysScreen
import com.spela.player.presentation.ui.screen.AllGamesScreen
import com.spela.player.presentation.ui.screen.CollectionDetailScreen
import com.spela.player.presentation.ui.screen.CollectionsScreen
import com.spela.player.presentation.ui.screen.FavoritesScreen
import com.spela.player.presentation.ui.screen.LibraryScreen
import com.spela.player.presentation.ui.screen.LicensesScreen
import com.spela.player.presentation.ui.screen.PlayLaterScreen
import com.spela.player.presentation.ui.screen.ServerConnectionScreen
import com.spela.player.presentation.ui.screen.SettingsScreen
import com.spela.player.presentation.ui.screen.ActivityScreen
import com.spela.player.presentation.ui.screen.ChallengeDetailScreen
import com.spela.player.presentation.ui.screen.ChallengeListScreen
import com.spela.player.presentation.ui.screen.GlobalChallengesScreen
import com.spela.player.presentation.ui.screen.SaveDataScreen
import com.spela.player.presentation.ui.screen.StatsScreen
import com.spela.player.presentation.ui.screen.UserProfileScreen
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
import com.spela.player.presentation.viewmodel.RelayDetailViewModel
import com.spela.player.presentation.viewmodel.RelaysViewModel
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.data.remote.PresenceService
import com.spela.player.presentation.viewmodel.NetplayLobbyViewModel
import com.spela.player.presentation.viewmodel.NetplayViewModel
import com.spela.player.presentation.viewmodel.ChallengeDetailViewModel
import com.spela.player.presentation.viewmodel.ChallengeListViewModel
import com.spela.player.presentation.viewmodel.CollectionsViewModel
import com.spela.player.presentation.viewmodel.GamepadConfigViewModel
import com.spela.player.presentation.viewmodel.SaveDataViewModel
import com.spela.player.presentation.viewmodel.SocialViewModel
import com.spela.player.presentation.viewmodel.StatsViewModel

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
    gamepadConfigViewModel: GamepadConfigViewModel? = null,
    socialViewModel: SocialViewModel,
    relaysViewModel: RelaysViewModel,
    relayDetailViewModel: RelayDetailViewModel,
    netplayViewModel: NetplayViewModel,
    netplayLobbyViewModel: NetplayLobbyViewModel,
    statsViewModel: StatsViewModel,
    collectionsViewModel: CollectionsViewModel,
    challengeListViewModel: ChallengeListViewModel,
    challengeDetailViewModel: ChallengeDetailViewModel,
    secondaryDisplay: PlatformSecondaryDisplay,
    presenceService: PresenceService,
    connectivityMonitor: ConnectivityMonitor,
    saveDataViewModel: SaveDataViewModel? = null,
) {
    val currentTheme by settingsViewModel.selectedTheme.collectAsState()

    SpelaTheme(theme = currentTheme) {
        val navState by navigationViewModel.state.collectAsState()

        // Hidden indicator for E2E tests: exposes whether the libretro core is running.
        // Tests wait for "Core idle" instead of Thread.sleep after exiting games.
        val coreIdleState by emulationViewModel.state.collectAsState()
        Box(
            modifier = Modifier
                .size(0.dp)
                .semantics {
                    contentDescription = if (coreIdleState.isRunning) "Core running" else "Core idle"
                },
        )

        // Show loading screen while session is being restored
        if (navState.isRestoringSession) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SpColor.Background),
                contentAlignment = Alignment.Center,
            ) {
                if (com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current) {
                    CircularProgressIndicator(color = SpColor.Primary)
                }
            }
            return@SpelaTheme
        }

        // Connect/disconnect WebSocket presence based on authentication state
        val isAuthenticated = navState.currentScreen !is SpScreen.ServerConnection &&
                navState.currentScreen !is SpScreen.Login
        LaunchedEffect(isAuthenticated) {
            if (isAuthenticated) {
                presenceService.connect()
            } else {
                presenceService.disconnect()
            }
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
            enabled = !navState.showInGameOverlay,
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
                // Offline banner
                val isOnline by connectivityMonitor.isOnline.collectAsState()
                SpOfflineBanner(isOffline = !isOnline)

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
                                val netplayState by netplayViewModel.state.collectAsState()
                                val activeNetplaySessions = netplayState.sessions.filter {
                                    it.status != NetplaySessionStatus.ENDED
                                }
                                LaunchedEffect(Unit) {
                                    netplayViewModel.onIntent(NetplayIntent.LoadSessions)
                                }
                                HomeScreen(
                                    viewModel = gameListViewModel,
                                    socialViewModel = socialViewModel,
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                    onNavigateToDownloads = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Downloads)
                                        )
                                    },
                                    onNavigateToFavorites = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Favorites)
                                        )
                                    },
                                    onNavigateToPlayLater = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.PlayLater)
                                        )
                                    },
                                    onNavigateToActivity = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Activity)
                                        )
                                    },
                                    onNavigateToStats = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Stats)
                                        )
                                    },
                                    onNavigateToChallenges = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GlobalChallenges)
                                        )
                                    },
                                    onChallengeSelected = { challengeId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ChallengeDetail(challengeId))
                                        )
                                    },
                                    onNetplaySessionSelected = { sessionId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.NetplayLobby(sessionId))
                                        )
                                    },
                                    onUserSelected = { userId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.UserProfile(userId))
                                        )
                                    },
                                    hasActiveDownloads = downloadsState.activeDownloads.isNotEmpty(),
                                    activeNetplaySessions = activeNetplaySessions,
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
                                // Refresh save states when returning from emulation overlay
                                LaunchedEffect(navState.showInGameOverlay) {
                                    if (!navState.showInGameOverlay && gameDetailViewModel.state.value.gameDetail != null) {
                                        gameDetailViewModel.onIntent(GameDetailIntent.RefreshSaves)
                                    }
                                }
                                val netplayState by netplayViewModel.state.collectAsState()
                                LaunchedEffect(netplayState.joinedSession) {
                                    netplayState.joinedSession?.let { session ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.NetplayLobby(session.id))
                                        )
                                    }
                                }
                                GameDetailScreen(
                                    gameId = screen.gameId,
                                    viewModel = gameDetailViewModel,
                                    keyMappingViewModel = keyMappingViewModel,
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                    onPlay = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.ShowOverlay(gameId)
                                        )
                                    },
                                    onPlayFresh = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.ShowOverlay(gameId, skipAutoLoad = true)
                                        )
                                    },
                                    onCreateNetplay = { gameId ->
                                        netplayViewModel.onIntent(
                                            NetplayIntent.CreateSession(gameId)
                                        )
                                    },
                                    onNavigateToChallenges = { gameId, gameTitle ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ChallengeList(gameId, gameTitle))
                                        )
                                    },
                                    onNavigateToRelay = { relayId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.RelayDetail(relayId))
                                        )
                                    },
                                    onNavigateToSaveData = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.SaveDataManagement(gameId))
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
                                    keyMappingViewModel = keyMappingViewModel,
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
                                    onNavigateToLicenses = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Licenses)
                                        )
                                    },
                                )
                            }

                            is SpScreen.ConsoleSettings -> {
                                ConsoleSettingsScreen(
                                    consoleId = screen.consoleId,
                                    settingsViewModel = settingsViewModel,
                                    keyMappingViewModel = keyMappingViewModel,
                                    gamepadConfigViewModel = gamepadConfigViewModel,
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.UserProfile -> {
                                UserProfileScreen(
                                    userId = screen.userId,
                                    socialViewModel = socialViewModel,
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

                            is SpScreen.Relays -> {
                                RelaysScreen(
                                    viewModel = relaysViewModel,
                                    onRelaySelected = { relayId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.RelayDetail(relayId))
                                        )
                                    },
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.RelayDetail -> {
                                RelayDetailScreen(
                                    relayId = screen.relayId,
                                    viewModel = relayDetailViewModel,
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                    onPlay = { gameId, relayId ->
                                        val turnToken = relayDetailViewModel.state.value.turnToken
                                        navigationViewModel.onIntent(
                                            NavigationIntent.ShowOverlay(
                                                gameId = gameId,
                                                relayId = relayId,
                                                turnToken = turnToken,
                                            )
                                        )
                                    },
                                )
                            }

                            is SpScreen.NetplaySessions -> {
                                NetplayListScreen(
                                    viewModel = netplayViewModel,
                                    onSessionSelected = { sessionId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.NetplayLobby(sessionId))
                                        )
                                    },
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.NetplayLobby -> {
                                val lobbyState by netplayLobbyViewModel.state.collectAsState()
                                NetplayLobbyScreen(
                                    sessionId = screen.sessionId,
                                    viewModel = netplayLobbyViewModel,
                                    currentUserId = lobbyState.currentUserId,
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                    onStartGame = { config ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.ShowOverlay(
                                                gameId = config.gameId,
                                                netplaySessionId = config.sessionId,
                                                netplayLocalPort = config.localPort,
                                                netplayInputDelay = config.inputDelay,
                                                netplayIsHost = config.isHost,
                                            )
                                        )
                                    },
                                )
                            }

                            is SpScreen.Library -> {
                                LibraryScreen(
                                    gameListViewModel = gameListViewModel,
                                    collectionsViewModel = collectionsViewModel,
                                    onConsoleSelected = { consoleId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Console(consoleId))
                                        )
                                    },
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                    onCollectionSelected = { collectionId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.CollectionDetail(collectionId))
                                        )
                                    },
                                )
                            }

                            is SpScreen.AllGames -> {
                                AllGamesScreen(
                                    viewModel = gameListViewModel,
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                )
                            }

                            is SpScreen.Favorites -> {
                                FavoritesScreen(
                                    viewModel = gameListViewModel,
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                )
                            }

                            is SpScreen.PlayLater -> {
                                PlayLaterScreen(
                                    viewModel = gameListViewModel,
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                )
                            }

                            is SpScreen.Collections -> {
                                CollectionsScreen(
                                    viewModel = collectionsViewModel,
                                    onCollectionSelected = { collectionId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.CollectionDetail(collectionId))
                                        )
                                    },
                                )
                            }

                            is SpScreen.CollectionDetail -> {
                                CollectionDetailScreen(
                                    collectionId = screen.collectionId,
                                    viewModel = collectionsViewModel,
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

                            is SpScreen.Stats -> {
                                StatsScreen(
                                    viewModel = statsViewModel,
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                    onUserSelected = { userId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.UserProfile(userId))
                                        )
                                    },
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.Activity -> {
                                ActivityScreen(
                                    viewModel = socialViewModel,
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                    onUserSelected = { userId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.UserProfile(userId))
                                        )
                                    },
                                    onNavigateToStats = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Stats)
                                        )
                                    },
                                )
                            }

                            is SpScreen.GlobalChallenges -> {
                                GlobalChallengesScreen(
                                    viewModel = challengeListViewModel,
                                    onChallengeSelected = { challengeId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ChallengeDetail(challengeId))
                                        )
                                    },
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.ChallengeList -> {
                                ChallengeListScreen(
                                    viewModel = challengeListViewModel,
                                    gameId = screen.gameId,
                                    gameTitle = screen.gameTitle,
                                    onChallengeSelected = { challengeId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ChallengeDetail(challengeId))
                                        )
                                    },
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.ChallengeDetail -> {
                                ChallengeDetailScreen(
                                    viewModel = challengeDetailViewModel,
                                    challengeId = screen.challengeId,
                                    onAttempt = { challengeId, gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.ShowOverlay(
                                                gameId = gameId,
                                                challengeId = challengeId,
                                            )
                                        )
                                    },
                                    onUserSelected = { userId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.UserProfile(userId))
                                        )
                                    },
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

                            is SpScreen.Licenses -> {
                                LicensesScreen(
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.SaveDataManagement -> {
                                if (saveDataViewModel != null) {
                                    SaveDataScreen(
                                        gameId = screen.gameId,
                                        viewModel = saveDataViewModel,
                                        onBack = {
                                            navigationViewModel.onIntent(NavigationIntent.GoBack)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // Emulation surface + in-game overlay
                    if (navState.showInGameOverlay) {
                        // Touch-blocking background: prevents touches from leaking through
                        // to the navigation screens (e.g. GameDetail) rendered behind.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent().changes.forEach { it.consume() }
                                        }
                                    }
                                },
                        )

                        LaunchedEffect(navState.overlayGameId, navState.overlayRelayId, navState.overlayNetplaySessionId, navState.overlayChallengeId) {
                            navState.overlayGameId?.let { gameId ->
                                emulationViewModel.onIntent(
                                    EmulationIntent.StartGame(
                                        gameId = gameId,
                                        relayId = navState.overlayRelayId,
                                        turnToken = navState.overlayTurnToken,
                                        netplaySessionId = navState.overlayNetplaySessionId,
                                        netplayLocalPort = navState.overlayNetplayLocalPort,
                                        netplayInputDelay = navState.overlayNetplayInputDelay,
                                        netplayIsHost = navState.overlayNetplayIsHost,
                                        challengeId = navState.overlayChallengeId,
                                        skipAutoLoad = navState.overlaySkipAutoLoad,
                                    )
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
                                netplayViewModel.onIntent(NetplayIntent.ClearJoinedSession)
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
                                if (com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current) {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            }
                        }

                        // Invisible semantic marker for E2E tests to detect that a game is running.
                        // Always on the primary display, regardless of dual-screen or controller type.
                        if (emulationState.isRunning) {
                            Box(modifier = Modifier.semantics {
                                contentDescription = "Game running"
                            })
                        }

                        // DS/3DS touch overlay on primary display (when secondary display is not active)
                        // Handles touch input on the bottom screen area of the rendered game
                        if (emulationState.isDualScreenConsole && !emulationState.secondaryDisplayActive
                            && emulationState.isRunning && !emulationState.showOverlay
                        ) {
                            DsPrimaryTouchOverlay(
                                controller = libretroController,
                                consoleId = emulationState.consoleId,
                                splitY = emulationState.dualScreenSplitY,
                            )
                        }

                        // Touch gamepad controls (Android only, no-op on desktop)
                        // Hidden when secondary display is active (controls move there)
                        // Also hidden for DS games (bottom screen touch replaces virtual buttons)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = emulationState.isRunning && !emulationState.showOverlay
                                && !emulationState.secondaryDisplayActive
                                && !emulationState.isDualScreenConsole,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            PlatformTouchControls(
                                controller = libretroController,
                            )
                        }

                        InGameOverlay(
                            viewModel = emulationViewModel,
                            keyMappingViewModel = keyMappingViewModel,
                            gamepadConfigViewModel = gamepadConfigViewModel,
                            onExit = {
                                navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                            },
                        )
                    }
                }

                // Bottom navigation bar
                val showBottomNav = !navState.showInGameOverlay && shouldShowBottomNav(navState.currentScreen)
                if (showBottomNav) {
                    SpBottomNavBar(
                        activeTab = activeTabForScreen(navState.currentScreen),
                        onTabSelected = { tab ->
                            val targetScreen = when (tab) {
                                BottomNavTab.HOME -> SpScreen.Home
                                BottomNavTab.LIBRARY -> SpScreen.Library
                                BottomNavTab.ACTIVITY -> SpScreen.Activity
                                BottomNavTab.SETTINGS -> SpScreen.Settings
                            }
                            navigationViewModel.onIntent(
                                NavigationIntent.NavigateTo(targetScreen)
                            )
                        },
                    )
                }
            }
        }
        }
    }
}

private fun shouldShowBottomNav(screen: SpScreen): Boolean = when (screen) {
    is SpScreen.ServerConnection, is SpScreen.Login -> false
    else -> true
}

private fun activeTabForScreen(screen: SpScreen): BottomNavTab = when (screen) {
    is SpScreen.Library, is SpScreen.AllGames, is SpScreen.Favorites,
    is SpScreen.PlayLater, is SpScreen.Collections, is SpScreen.CollectionDetail,
    is SpScreen.Console -> BottomNavTab.LIBRARY
    is SpScreen.Activity, is SpScreen.Stats -> BottomNavTab.ACTIVITY
    is SpScreen.Settings, is SpScreen.ConsoleSettings, is SpScreen.Licenses -> BottomNavTab.SETTINGS
    else -> BottomNavTab.HOME
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
        )
    }
}
