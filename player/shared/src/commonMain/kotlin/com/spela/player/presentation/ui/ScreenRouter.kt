package com.spela.player.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.spela.player.domain.model.NetplaySessionStatus
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.intent.NetplayIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationState
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.screen.ActivityScreen
import com.spela.player.presentation.ui.screen.AllGamesScreen
import com.spela.player.presentation.ui.screen.ChallengeDetailScreen
import com.spela.player.presentation.ui.screen.ChallengeListScreen
import com.spela.player.presentation.ui.screen.CollectionDetailScreen
import com.spela.player.presentation.ui.screen.CollectionsScreen
import com.spela.player.presentation.ui.screen.ConsoleGamesScreen
import com.spela.player.presentation.ui.screen.ConsoleScreen
import com.spela.player.presentation.ui.screen.ConsoleSettingsScreen
import com.spela.player.presentation.ui.screen.ConsolesScreen
import com.spela.player.presentation.ui.screen.DeveloperGamesScreen
import com.spela.player.presentation.ui.screen.DownloadsScreen
import com.spela.player.presentation.ui.screen.ExploreDeveloperScreen
import com.spela.player.presentation.ui.screen.ExploreFranchiseScreen
import com.spela.player.presentation.ui.screen.ExploreGalleryScreen
import com.spela.player.presentation.ui.screen.ExploreKeywordScreen
import com.spela.player.presentation.ui.screen.ExploreMoodScreen
import com.spela.player.presentation.ui.screen.ExplorePublisherScreen
import com.spela.player.presentation.ui.screen.ExploreScreen
import com.spela.player.presentation.ui.screen.ExploreSearchScreen
import com.spela.player.presentation.ui.screen.ExploreSeriesScreen
import com.spela.player.presentation.ui.screen.ExploreThemeScreen
import com.spela.player.presentation.ui.screen.ExploreWizardScreen
import com.spela.player.presentation.ui.screen.FavoritesScreen
import com.spela.player.presentation.ui.screen.GameAchievementsScreen
import com.spela.player.presentation.ui.screen.GameDetailScreen
import com.spela.player.presentation.ui.screen.GlobalChallengesScreen
import com.spela.player.presentation.ui.screen.GlobalSearchScreen
import com.spela.player.presentation.ui.screen.HomeScreen
import com.spela.player.presentation.ui.screen.LicensesScreen
import com.spela.player.presentation.ui.screen.LoginScreen
import com.spela.player.presentation.ui.screen.NetplayListScreen
import com.spela.player.presentation.ui.screen.NetplayLobbyScreen
import com.spela.player.presentation.ui.screen.PlayLaterScreen
import com.spela.player.presentation.ui.screen.ServerConnectionScreen
import com.spela.player.presentation.ui.screen.SessionDetailScreen
import com.spela.player.presentation.ui.screen.SettingsScreen
import com.spela.player.presentation.ui.screen.SharedSessionDetailScreen
import com.spela.player.presentation.ui.screen.SharedSessionsScreen
import com.spela.player.presentation.ui.screen.StatsScreen
import com.spela.player.presentation.ui.screen.TopListsScreen
import com.spela.player.presentation.ui.screen.UserProfileScreen

/**
 * The 43-branch `when (screen)` router lifted out of [SpelaApp] in
 * #693 PR B. Pulls dependencies from [SpelaAppDependencies] via
 * `with(deps)` so the ported `when` branches continue to resolve
 * bare VM names (navigationViewModel, gameDetailViewModel, …)
 * without edit. Navigation state comes from the VM one level up in
 * [SpelaApp] and is threaded through here so the screens can read
 * overlay / restore fields.
 *
 * Deliberately keeps the same direct `navigationViewModel.onIntent(
 * NavigationIntent.NavigateTo(x))` pattern the inline router used —
 * no closure abstraction this PR. Introducing a `navigate: (SpScreen)
 * -> Unit` parameter would either add a mechanical 100+ line
 * callback bag or force every branch to be rewritten. Do that
 * later if it proves useful.
 */
@Composable
fun ScreenRouter(
    screen: SpScreen,
    deps: SpelaAppDependencies,
    navState: NavigationState,
) = with(deps) {
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
                                        // Reset the whole tab system rather than
                                        // pushing Home onto the active tab's stack —
                                        // the user signed out from inside Settings (or
                                        // any other tab), so the active stack is full
                                        // of pre-logout breadcrumbs that pushing Home
                                        // would just hide. ResetToHome rebuilds tabStacks
                                        // from defaults and sets activeTab=HOME so the
                                        // user lands on a clean state.
                                        navigationViewModel.onIntent(
                                            NavigationIntent.ResetToHome
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
                                    overlayClosedTick = navState.overlayClosedTick,
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
                                    ExplorePublisherScreen(
                                        name = screen.name,
                                        viewModel = exploreViewModel,
                                        onGameSelected = { gameId ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.GameDetail(gameId))
                                            )
                                        },
                                        onDeveloperSelected = { developerName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExploreDeveloper(developerName))
                                            )
                                        },
                                        onPublisherSelected = { publisherName ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.ExplorePublisher(publisherName))
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
                                    overlayClosedTick = navState.overlayClosedTick,
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
                                    onNavigateToSession = { newSessionId ->
                                        navigationViewModel.onIntent(
                                            NavigationIntent.NavigateTo(SpScreen.SessionDetail(newSessionId))
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
                                    overlayClosedTick = navState.overlayClosedTick,
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
                                        onNavigateToSession = { newSessionId ->
                                            navigationViewModel.onIntent(
                                                NavigationIntent.NavigateTo(SpScreen.SessionDetail(newSessionId))
                                            )
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
}
