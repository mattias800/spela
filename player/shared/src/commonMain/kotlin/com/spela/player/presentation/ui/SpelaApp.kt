package com.spela.player.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.domain.model.NetplaySessionStatus
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.intent.NetplayIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.components.BottomNavTab
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.NavigationLayoutMode
import com.spela.player.presentation.ui.components.SpBottomNavBar
import com.spela.player.presentation.ui.components.SpNavigationRail
import com.spela.player.presentation.ui.components.SpAuthExpiredDialog
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpDatabaseErrorScreen
import com.spela.player.presentation.ui.components.SpOfflineBanner
import com.spela.player.presentation.ui.components.SpServerWarningCard
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.data.remote.ConnectionState
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.presentation.navigation.NavigationEvent
import com.spela.player.presentation.navigation.NavigationEventBus
import com.spela.player.presentation.ui.feature.ingame.DsPrimaryTouchOverlay
import com.spela.player.presentation.ui.screen.ConsoleGamesScreen
import com.spela.player.presentation.ui.screen.DeveloperGamesScreen
import com.spela.player.presentation.ui.screen.ConsoleScreen
import com.spela.player.presentation.ui.screen.ConsoleSettingsScreen
import com.spela.player.presentation.ui.screen.ConsolesScreen
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
import com.spela.player.presentation.ui.screen.SharedSessionDetailScreen
import com.spela.player.presentation.ui.screen.SessionDetailScreen
import com.spela.player.presentation.ui.screen.SharedSessionsScreen
import com.spela.player.presentation.ui.screen.AllGamesScreen
import com.spela.player.presentation.ui.screen.CollectionDetailScreen
import com.spela.player.presentation.ui.screen.CollectionsScreen
import com.spela.player.presentation.ui.screen.FavoritesScreen
import com.spela.player.presentation.ui.screen.LicensesScreen
import com.spela.player.presentation.ui.screen.PlayLaterScreen
import com.spela.player.presentation.ui.screen.ServerConnectionScreen
import com.spela.player.presentation.ui.screen.SettingsScreen
import com.spela.player.presentation.ui.screen.ActivityScreen
import com.spela.player.presentation.ui.screen.ChallengeDetailScreen
import com.spela.player.presentation.ui.screen.GameAchievementsScreen
import com.spela.player.presentation.ui.screen.ChallengeListScreen
import com.spela.player.presentation.ui.screen.GlobalChallengesScreen
import com.spela.player.presentation.ui.screen.StatsScreen
import com.spela.player.presentation.ui.screen.ExploreDeveloperScreen
import com.spela.player.presentation.ui.screen.ExploreGalleryScreen
import com.spela.player.presentation.ui.screen.ExploreKeywordScreen
import com.spela.player.presentation.ui.screen.ExploreMoodScreen
import com.spela.player.presentation.ui.screen.ExploreScreen
import com.spela.player.presentation.ui.screen.ExploreSeriesScreen
import com.spela.player.presentation.ui.screen.ExploreFranchiseScreen
import com.spela.player.presentation.ui.screen.ExploreSearchScreen
import com.spela.player.presentation.ui.screen.GlobalSearchScreen
import com.spela.player.presentation.ui.screen.ExploreWizardScreen
import com.spela.player.presentation.ui.screen.ExploreThemeScreen
import com.spela.player.presentation.ui.screen.TopListsScreen
import com.spela.player.presentation.ui.screen.UserProfileScreen
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.gamepad.GamepadHandler
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.components.LocalScrapeService
import com.spela.player.presentation.ui.components.ScrapeUpdates
import com.spela.player.presentation.ui.theme.SpelaTheme
import com.spela.player.data.remote.ScrapeService
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.PointerEventPass
import com.spela.player.presentation.viewmodel.DownloadsViewModel
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.GameDetailViewModel
import com.spela.player.presentation.viewmodel.GameListViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.presentation.viewmodel.LoginViewModel
import com.spela.player.presentation.viewmodel.SharedSessionDetailViewModel
import com.spela.player.presentation.viewmodel.SessionDetailViewModel
import com.spela.player.presentation.viewmodel.SharedSessionsViewModel
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.data.remote.PresenceService
import com.spela.player.presentation.viewmodel.NetplayLobbyViewModel
import com.spela.player.presentation.viewmodel.NetplayViewModel
import com.spela.player.presentation.viewmodel.ChallengeDetailViewModel
import com.spela.player.presentation.viewmodel.ChallengeListViewModel
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.presentation.viewmodel.GlobalSearchViewModel
import com.spela.player.presentation.viewmodel.CollectionsViewModel
import com.spela.player.presentation.viewmodel.GamepadConfigViewModel
import com.spela.player.presentation.viewmodel.SocialViewModel
import com.spela.player.presentation.viewmodel.StatsViewModel
import com.spela.player.presentation.viewmodel.TopListsViewModel
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.ui.components.SpSectionIndicator

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
    sharedSessionsViewModel: SharedSessionsViewModel,
    sharedSessionDetailViewModel: SharedSessionDetailViewModel,
    netplayViewModel: NetplayViewModel,
    netplayLobbyViewModel: NetplayLobbyViewModel,
    statsViewModel: StatsViewModel,
    collectionsViewModel: CollectionsViewModel,
    challengeListViewModel: ChallengeListViewModel,
    challengeDetailViewModel: ChallengeDetailViewModel,
    secondaryDisplay: PlatformSecondaryDisplay,
    presenceService: PresenceService,
    connectivityMonitor: ConnectivityMonitor,
    sessionDetailViewModel: SessionDetailViewModel? = null,
    topListsViewModel: TopListsViewModel? = null,
    exploreViewModel: ExploreViewModel? = null,
    navigationEventBus: NavigationEventBus? = null,
    gamepadPortManager: GamepadPortManager? = null,
    globalSearchViewModel: GlobalSearchViewModel? = null,
    scrapeService: ScrapeService? = null,
) {
    val currentTheme by settingsViewModel.selectedTheme.collectAsState()

    SpelaTheme(theme = currentTheme) {
    CompositionLocalProvider(
        LocalScrapeService provides scrapeService,
        LocalInputMode provides (gamepadPortManager?.inputMode?.collectAsState()?.value ?: InputMode.TOUCH),
    ) {
        // Observe scrape completions and update cover art reactively
        LaunchedEffect(scrapeService) {
            scrapeService?.scrapedGames?.collect { game ->
                ScrapeUpdates.onGameScraped(game)
            }
        }

        val navState by navigationViewModel.state.collectAsState()

        // Input mode detection: TOUCH shows tab bar, GAMEPAD shows section indicator
        val inputMode by gamepadPortManager?.inputMode?.collectAsState()
            ?: remember { mutableStateOf(InputMode.TOUCH) }
        val isGamepadMode = inputMode == InputMode.GAMEPAD
        val sectionIndicatorVisible = isGamepadMode

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
            return@CompositionLocalProvider
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

        // Collect desktop gamepad navigation events
        if (navigationEventBus != null) {
            LaunchedEffect(navigationEventBus) {
                navigationEventBus.events.collect { event ->
                    when (event) {
                        NavigationEvent.NextSection -> navigationViewModel.onIntent(NavigationIntent.NextSection)
                        NavigationEvent.PreviousSection -> navigationViewModel.onIntent(NavigationIntent.PreviousSection)
                    }
                }
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
            onNextSection = if (isGamepadScreen) {
                { navigationViewModel.onIntent(NavigationIntent.NextSection) }
            } else null,
            onPreviousSection = if (isGamepadScreen) {
                { navigationViewModel.onIntent(NavigationIntent.PreviousSection) }
            } else null,
            onGamepadInput = { gamepadPortManager?.setInputMode(InputMode.GAMEPAD) },
            focusResetKey = if (isGamepadMode) navState.currentScreen else null,
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background)
                .onPreviewKeyEvent { keyEvent ->
                    // Cmd+K (macOS) / Ctrl+K (Windows/Linux) opens global search
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.K &&
                        (keyEvent.isMetaPressed || keyEvent.isCtrlPressed) &&
                        !navState.showInGameOverlay
                    ) {
                        if (navState.currentScreen !is SpScreen.GlobalSearch) {
                            navigationViewModel.onIntent(
                                NavigationIntent.NavigateTo(SpScreen.GlobalSearch)
                            )
                        }
                        true
                    } else {
                        false
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) {
                                gamepadPortManager?.setInputMode(InputMode.TOUCH)
                            }
                        }
                    }
                },
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val navLayoutMode = when {
                maxWidth > 840.dp -> NavigationLayoutMode.LABELED_RAIL
                maxWidth > 600.dp -> NavigationLayoutMode.ICON_RAIL
                else -> NavigationLayoutMode.BOTTOM_BAR
            }
            val showNavArea = !navState.showInGameOverlay && shouldShowBottomNav(navState.currentScreen)
            val showSideRail = navLayoutMode != NavigationLayoutMode.BOTTOM_BAR && showNavArea && !isGamepadMode

            Row(modifier = Modifier.fillMaxSize()) {
            // Side navigation rail (larger screens, touch mode only)
            if (showSideRail) {
                SpNavigationRail(
                    activeTab = activeTabForScreen(navState.currentScreen),
                    onTabSelected = { tab ->
                        val targetScreen = when (tab) {
                            BottomNavTab.HOME -> SpScreen.Home
                            BottomNavTab.EXPLORE -> SpScreen.Explore
                            BottomNavTab.CONSOLES -> SpScreen.Consoles
                            BottomNavTab.COLLECTIONS -> SpScreen.Collections
                            BottomNavTab.ACTIVITY -> SpScreen.Activity
                            BottomNavTab.SETTINGS -> SpScreen.Settings
                        }
                        navigationViewModel.onIntent(
                            NavigationIntent.SwitchTab(targetScreen)
                        )
                    },
                    showLabels = navLayoutMode == NavigationLayoutMode.LABELED_RAIL,
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Connection state
                val currentConnectionState by connectivityMonitor.connectionState.collectAsState()
                var serverWarningDismissed by remember { mutableStateOf(false) }
                var snackbarData by remember { mutableStateOf<SpSnackbarData?>(null) }

                // "Back online" snackbar: fires only after a genuine offline→online transition
                var hasBeenOffline by remember { mutableStateOf(false) }
                LaunchedEffect(currentConnectionState) {
                    if (currentConnectionState is ConnectionState.Online) {
                        if (hasBeenOffline) {
                            snackbarData = SpSnackbarData(
                                message = "Back online",
                                type = SpSnackbarType.Success,
                                durationMs = 3000L,
                            )
                        }
                    } else if (!currentConnectionState.isConnected) {
                        hasBeenOffline = true
                    }
                    // Reset dismiss when state changes away from ServerUnreachable
                    if (currentConnectionState !is ConnectionState.ServerUnreachable) {
                        serverWarningDismissed = false
                    }
                }

                // Priority: DatabaseError (full-screen) > AuthFailed (dialog) > ServerUnreachable (card) > Offline (banner)
                if (currentConnectionState is ConnectionState.DatabaseError) {
                    SpDatabaseErrorScreen(
                        message = (currentConnectionState as ConnectionState.DatabaseError).message,
                        onResetApp = { navigationViewModel.resetDatabase() },
                    )
                } else {

                if (currentConnectionState is ConnectionState.AuthFailed) {
                    SpAuthExpiredDialog(
                        onSignIn = {
                            connectivityMonitor.clearAuthFailure()
                            navigationViewModel.onIntent(
                                NavigationIntent.NavigateTo(SpScreen.Login)
                            )
                        },
                        onContinueOffline = {
                            connectivityMonitor.clearAuthFailure()
                        },
                    )
                }

                // Offline banner (muted grey)
                SpOfflineBanner(connectionState = currentConnectionState)

                // Server unreachable card (dismissible per-session)
                if (currentConnectionState is ConnectionState.ServerUnreachable && !serverWarningDismissed) {
                    SpServerWarningCard(
                        onCheckServerSettings = {
                            navigationViewModel.onIntent(
                                NavigationIntent.NavigateTo(SpScreen.Settings)
                            )
                        },
                        onDismiss = { serverWarningDismissed = true },
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    val animationsEnabled = com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current
                    val saveableStateHolder = rememberSaveableStateHolder()

                    // When navigating forward to a NEW screen, clear saved state so
                    // it starts fresh (e.g., scroll position at top).
                    // Preserve state when going back OR switching tabs — tab screens
                    // should show cached data immediately instead of a loading spinner.
                    if (!navState.isGoingBack && !navState.isTabSwitch) {
                        saveableStateHolder.removeState(navState.currentScreen.route)
                    }

                    AnimatedContent(
                        targetState = navState.currentScreen,
                        transitionSpec = {
                            if (!animationsEnabled || navState.isTabSwitch) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else if (navState.isGoingBack) {
                                (slideInHorizontally { -it / 3 } + fadeIn())
                                    .togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
                            } else {
                                (slideInHorizontally { it / 3 } + fadeIn())
                                    .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                            }
                        },
                    ) { screen ->
                        saveableStateHolder.SaveableStateProvider(screen.route) {
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
                                // Refresh dashboard when returning from emulation
                                LaunchedEffect(navState.showInGameOverlay) {
                                    if (!navState.showInGameOverlay) {
                                        gameListViewModel.onIntent(GameListIntent.LoadDashboard)
                                    }
                                }
                                HomeScreen(
                                    viewModel = gameListViewModel,
                                    socialViewModel = socialViewModel,
                                    settingsViewModel = settingsViewModel,
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
                                    onSearchSelected = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GlobalSearch)
                                        )
                                    },
                                    hasActiveDownloads = downloadsState.activeDownloads.isNotEmpty(),
                                    activeNetplaySessions = activeNetplaySessions,
                                )
                            }

                            is SpScreen.Explore -> {
                                if (exploreViewModel != null) {
                                    ExploreScreen(
                                        viewModel = exploreViewModel,
                                        onGameSelected = { gameId ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                            )
                                        },
                                        onChallengeSelected = { challengeId ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ChallengeDetail(challengeId))
                                            )
                                        },
                                        onThemeSelected = { themeId, themeName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreTheme(themeId, themeName))
                                            )
                                        },
                                        onKeywordSelected = { keywordId, keywordName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreKeyword(keywordId, keywordName))
                                            )
                                        },
                                        onSeriesSelected = { seriesId, seriesName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreSeries(seriesId, seriesName))
                                            )
                                        },
                                        onMoodSelected = { moodId, moodName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreMood(moodId, moodName))
                                            )
                                        },
                                        onDeveloperSelected = { name ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper(name))
                                            )
                                        },
                                        onConsoleSelected = { consoleId ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.Console(consoleId))
                                            )
                                        },
                                        onGallerySelected = {
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreGallery)
                                            )
                                        },
                                        onSurpriseMe = {
                                            exploreViewModel.loadSurpriseGame { gameId ->
                                                navigationViewModel.onIntent(
                                                    NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                                )
                                            }
                                        },
                                        onGlobalSearchSelected = {
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.GlobalSearch)
                                            )
                                        },
                                        onSearchSelected = {
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreSearch)
                                            )
                                        },
                                        onWizardSelected = {
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreWizard)
                                            )
                                        },
                                    )
                                }
                            }

                            is SpScreen.ExploreTheme -> {
                                if (exploreViewModel != null) {
                                    ExploreThemeScreen(
                                        themeId = screen.themeId,
                                        themeName = screen.themeName,
                                        viewModel = exploreViewModel,
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
                            }

                            is SpScreen.ExploreKeyword -> {
                                if (exploreViewModel != null) {
                                    ExploreKeywordScreen(
                                        keywordId = screen.keywordId,
                                        keywordName = screen.keywordName,
                                        viewModel = exploreViewModel,
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
                            }

                            is SpScreen.ExploreSeries -> {
                                if (exploreViewModel != null) {
                                    ExploreSeriesScreen(
                                        seriesId = screen.seriesId,
                                        seriesName = screen.seriesName,
                                        viewModel = exploreViewModel,
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
                            }

                            is SpScreen.ExploreFranchise -> {
                                if (exploreViewModel != null) {
                                    ExploreFranchiseScreen(
                                        franchiseId = screen.franchiseId,
                                        franchiseName = screen.franchiseName,
                                        viewModel = exploreViewModel,
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
                            }

                            is SpScreen.ExploreMood -> {
                                if (exploreViewModel != null) {
                                    ExploreMoodScreen(
                                        moodId = screen.moodId,
                                        moodName = screen.moodName,
                                        viewModel = exploreViewModel,
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
                            }

                            is SpScreen.ExploreDeveloper -> {
                                if (exploreViewModel != null) {
                                    ExploreDeveloperScreen(
                                        name = screen.name,
                                        isDeveloper = true,
                                        viewModel = exploreViewModel,
                                        onGameSelected = { gameId ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                            )
                                        },
                                        onPublisherSelected = { publisherName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExplorePublisher(publisherName))
                                            )
                                        },
                                        onDeveloperSelected = { developerName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper(developerName))
                                            )
                                        },
                                        onNavigateToGames = { devName, isDev ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.DeveloperGames(devName, isDev))
                                            )
                                        },
                                        onBack = {
                                            navigationViewModel.onIntent(NavigationIntent.GoBack)
                                        },
                                    )
                                }
                            }

                            is SpScreen.ExplorePublisher -> {
                                if (exploreViewModel != null) {
                                    ExploreDeveloperScreen(
                                        name = screen.name,
                                        isDeveloper = false,
                                        viewModel = exploreViewModel,
                                        onGameSelected = { gameId ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                            )
                                        },
                                        onPublisherSelected = { publisherName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExplorePublisher(publisherName))
                                            )
                                        },
                                        onDeveloperSelected = { developerName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper(developerName))
                                            )
                                        },
                                        onNavigateToGames = { devName, isDev ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.DeveloperGames(devName, isDev))
                                            )
                                        },
                                        onBack = {
                                            navigationViewModel.onIntent(NavigationIntent.GoBack)
                                        },
                                    )
                                }
                            }

                            is SpScreen.DeveloperGames -> {
                                if (exploreViewModel != null) {
                                    DeveloperGamesScreen(
                                        name = screen.name,
                                        isDeveloper = screen.isDeveloper,
                                        viewModel = exploreViewModel,
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
                            }

                            is SpScreen.ExploreGallery -> {
                                if (exploreViewModel != null) {
                                    ExploreGalleryScreen(
                                        viewModel = exploreViewModel,
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
                            }

                            is SpScreen.GlobalSearch -> {
                                if (globalSearchViewModel != null) {
                                    GlobalSearchScreen(
                                        viewModel = globalSearchViewModel,
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
                                        onDeveloperSelected = { name ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper(name))
                                            )
                                        },
                                        onPublisherSelected = { name ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExplorePublisher(name))
                                            )
                                        },
                                        onCollectionSelected = { collectionId ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.CollectionDetail(collectionId))
                                            )
                                        },
                                        onSeriesSelected = { seriesId, seriesName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreSeries(seriesId, seriesName))
                                            )
                                        },
                                        onFranchiseSelected = { franchiseId, franchiseName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreFranchise(franchiseId, franchiseName))
                                            )
                                        },
                                        onAdvancedFiltersSelected = {
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreSearch)
                                            )
                                        },
                                        onBack = {
                                            navigationViewModel.onIntent(NavigationIntent.GoBack)
                                        },
                                    )
                                }
                            }

                            is SpScreen.ExploreSearch -> {
                                if (exploreViewModel != null) {
                                    ExploreSearchScreen(
                                        viewModel = exploreViewModel,
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
                            }

                            is SpScreen.ExploreWizard -> {
                                if (exploreViewModel != null) {
                                    ExploreWizardScreen(
                                        viewModel = exploreViewModel,
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
                            }

                            is SpScreen.Console -> {
                                ConsoleScreen(
                                    consoleId = screen.consoleId,
                                    viewModel = gameListViewModel,
                                    exploreViewModel = exploreViewModel,
                                    onGameSelected = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
                                    },
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                    onDeveloperSelected = { name ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper(name))
                                        )
                                    },
                                    onNavigateToConsoleSettings = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ConsoleSettings(screen.consoleId))
                                        )
                                    },
                                    onBrowseAllGames = {
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ConsoleGames(screen.consoleId))
                                        )
                                    },
                                )
                            }

                            is SpScreen.ConsoleGames -> {
                                ConsoleGamesScreen(
                                    consoleId = screen.consoleId,
                                    viewModel = gameListViewModel,
                                    onGameSelected = { gameId: String ->
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
                                // Refresh sessions when returning from emulation overlay
                                LaunchedEffect(navState.showInGameOverlay) {
                                    if (!navState.showInGameOverlay && gameDetailViewModel.state.value.gameDetail != null) {
                                        gameDetailViewModel.state.value.gameDetail?.game?.id?.let { gameId ->
                                            gameDetailViewModel.onIntent(GameDetailIntent.LoadSessions(gameId))
                                        }
                                    }
                                }
                                val netplayState by netplayViewModel.state.collectAsState()
                                LaunchedEffect(netplayState.joinedSession) {
                                    netplayState.joinedSession?.let { session ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.NetplayLobby(session.id))
                                        )
                                        netplayViewModel.onIntent(NetplayIntent.ClearJoinedSession)
                                    }
                                }
                                val syncState by emulationViewModel.syncState.collectAsState()
                                LaunchedEffect(Unit) {
                                    emulationViewModel.launchReady.collect { pending ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.ShowOverlay(
                                                gameId = pending.gameId,
                                                skipAutoLoad = pending.skipAutoLoad,
                                                forceNewSession = pending.forceNewSession,
                                                sessionId = pending.sessionId,
                                            )
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
                                        emulationViewModel.onIntent(
                                            EmulationIntent.PrepareLaunch(gameId)
                                        )
                                    },
                                    onPlayFresh = { gameId ->
                                        emulationViewModel.onIntent(
                                            EmulationIntent.PrepareLaunch(gameId, skipAutoLoad = true, forceNewSession = true)
                                        )
                                    },
                                    onPlayFromTitleScreen = { gameId ->
                                        emulationViewModel.onIntent(
                                            EmulationIntent.PrepareLaunch(gameId, skipAutoLoad = true, forceNewSession = false)
                                        )
                                    },
                                    syncState = syncState.takeIf { it?.gameId == screen.gameId },
                                    onPlayWithLocalSave = {
                                        emulationViewModel.onIntent(EmulationIntent.PlayWithLocalSave)
                                    },
                                    onCancelLaunch = {
                                        emulationViewModel.onIntent(EmulationIntent.CancelLaunch)
                                    },
                                    onPlaySession = { gameId, sessionId ->
                                        emulationViewModel.onIntent(
                                            EmulationIntent.PrepareLaunch(gameId, sessionId = sessionId)
                                        )
                                    },
                                    onPlaySessionFromTitleScreen = { gameId, sessionId ->
                                        emulationViewModel.onIntent(
                                            EmulationIntent.PrepareLaunch(gameId, skipAutoLoad = true, sessionId = sessionId)
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
                                    onNavigateToSharedSession = { sharedSessionId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail(sharedSessionId))
                                        )
                                    },
                                    onNavigateToSession = { sid ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.SessionDetail(sid))
                                        )
                                    },
                                    onNavigateToGame = { targetGameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(targetGameId))
                                        )
                                    },
                                    onNavigateToUser = { userId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.UserProfile(userId))
                                        )
                                    },
                                    onNavigateToSeries = { seriesId, seriesName ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ExploreSeries(seriesId, seriesName))
                                        )
                                    },
                                    onNavigateToFranchise = { franchiseId, franchiseName ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ExploreFranchise(franchiseId, franchiseName))
                                        )
                                    },
                                    onNavigateToDeveloper = { name ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper(name))
                                        )
                                    },
                                    onNavigateToPublisher = { name ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.ExplorePublisher(name))
                                        )
                                    },
                                    onNavigateToAchievements = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameAchievements(gameId))
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
                                    onGameClick = { gameId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                        )
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

                            is SpScreen.SharedSessions -> {
                                SharedSessionsScreen(
                                    viewModel = sharedSessionsViewModel,
                                    onSharedSessionSelected = { sharedSessionId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.SharedSessionDetail(sharedSessionId))
                                        )
                                    },
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                )
                            }

                            is SpScreen.SharedSessionDetail -> {
                                SharedSessionDetailScreen(
                                    sharedSessionId = screen.sharedSessionId,
                                    viewModel = sharedSessionDetailViewModel,
                                    onBack = {
                                        navigationViewModel.onIntent(NavigationIntent.GoBack)
                                    },
                                    onPlay = { gameId, sharedSessionId ->
                                        val turnToken = sharedSessionDetailViewModel.state.value.turnToken
                                        navigationViewModel.onIntent(
                                            NavigationIntent.ShowOverlay(
                                                gameId = gameId,
                                                sharedSessionId = sharedSessionId,
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

                            is SpScreen.Consoles -> {
                                ConsolesScreen(
                                    viewModel = gameListViewModel,
                                    onConsoleSelected = { consoleId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.Console(consoleId))
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

                            is SpScreen.GameAchievements -> {
                                GameAchievementsScreen(
                                    gameId = screen.gameId,
                                    viewModel = gameDetailViewModel,
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

                            is SpScreen.SessionDetail -> {
                                if (sessionDetailViewModel != null) {
                                    SessionDetailScreen(
                                        sessionId = screen.sessionId,
                                        viewModel = sessionDetailViewModel,
                                        onBack = {
                                            navigationViewModel.onIntent(NavigationIntent.GoBack)
                                        },
                                        onPlay = { gameId, sid ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.ShowOverlay(
                                                    gameId = gameId,
                                                    sessionId = sid,
                                                )
                                            )
                                        },
                                        onDeleted = {
                                            navigationViewModel.onIntent(NavigationIntent.GoBack)
                                        },
                                    )
                                }
                            }

                            is SpScreen.TopLists -> {
                                if (topListsViewModel != null) {
                                    TopListsScreen(
                                        viewModel = topListsViewModel,
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
                            }
                        } // when (screen)
                        } // SaveableStateProvider
                    } // AnimatedContent

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

                        LaunchedEffect(navState.overlayGameId, navState.overlaySharedSessionId, navState.overlayNetplaySessionId, navState.overlayChallengeId, navState.overlaySessionId, navState.overlaySkipAutoLoad, navState.overlayForceNewSession) {
                            navState.overlayGameId?.let { gameId ->
                                emulationViewModel.onIntent(
                                    EmulationIntent.StartGame(
                                        gameId = gameId,
                                        sharedSessionId = navState.overlaySharedSessionId,
                                        turnToken = navState.overlayTurnToken,
                                        netplaySessionId = navState.overlayNetplaySessionId,
                                        netplayLocalPort = navState.overlayNetplayLocalPort,
                                        netplayInputDelay = navState.overlayNetplayInputDelay,
                                        netplayIsHost = navState.overlayNetplayIsHost,
                                        challengeId = navState.overlayChallengeId,
                                        skipAutoLoad = navState.overlaySkipAutoLoad,
                                        forceNewSession = navState.overlayForceNewSession,
                                        sessionId = navState.overlaySessionId,
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

                        // Missing BIOS dialog (AC 4.3)
                        if (emulationState.showMissingBiosDialog) {
                            com.spela.player.presentation.ui.feature.gamedetail.MissingBiosDialog(
                                consoleName = emulationState.missingBiosConsoleName,
                                missingFiles = emulationState.missingBiosFiles,
                                onGoBack = {
                                    emulationViewModel.onIntent(EmulationIntent.DismissMissingBiosDialog)
                                    navigationViewModel.onIntent(NavigationIntent.HideOverlay)
                                },
                                onTryAnyway = {
                                    emulationViewModel.onIntent(EmulationIntent.TryAnywayMissingBios)
                                },
                            )
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

                // "Back online" snackbar
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    SpSnackbar(
                        data = snackbarData,
                        onDismiss = { snackbarData = null },
                    )
                }

                // Bottom navigation bar (phones only, hidden when in gamepad mode or side rail is showing)
                if (navLayoutMode == NavigationLayoutMode.BOTTOM_BAR && showNavArea && !isGamepadMode) {
                    SpBottomNavBar(
                        activeTab = activeTabForScreen(navState.currentScreen),
                        onTabSelected = { tab ->
                            val targetScreen = when (tab) {
                                BottomNavTab.HOME -> SpScreen.Home
                                BottomNavTab.EXPLORE -> SpScreen.Explore
                                BottomNavTab.CONSOLES -> SpScreen.Consoles
                                BottomNavTab.COLLECTIONS -> SpScreen.Collections
                                BottomNavTab.ACTIVITY -> SpScreen.Activity
                                BottomNavTab.SETTINGS -> SpScreen.Settings
                            }
                            navigationViewModel.onIntent(
                                NavigationIntent.SwitchTab(targetScreen)
                            )
                        },
                    )
                }
                } // else (not DatabaseError)
            } // Column (content + bottom bar)
            } // Row (side rail + content column)

            // Section indicator overlay (shown when in gamepad mode)
            if (showNavArea && isGamepadMode) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    SpSectionIndicator(
                        activeTab = activeTabForScreen(navState.currentScreen),
                        visible = sectionIndicatorVisible,
                        modifier = Modifier.padding(top = SpSpacing.Default),
                    )
                }
            }
            } // BoxWithConstraints
        } // Box
        } // GamepadNavigation
    } // CompositionLocalProvider
    } // SpelaTheme
} // SpelaApp

private fun shouldShowBottomNav(screen: SpScreen): Boolean = when (screen) {
    is SpScreen.ServerConnection, is SpScreen.Login -> false
    else -> true
}

private fun activeTabForScreen(screen: SpScreen): BottomNavTab =
    NavigationViewModel.activeTabForScreen(screen)

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
