package com.spela.player.presentation.navigation

import com.spela.player.data.local.DatabaseHealthCheck
import com.spela.player.data.local.DatabaseResetHelper
import com.spela.player.data.remote.ConnectionState
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.SyncEngine
import com.spela.player.data.repository.BiosRepository
import com.spela.player.domain.usecase.RestoreSessionResult
import com.spela.player.domain.usecase.RestoreSessionUseCase
import com.spela.player.presentation.ui.components.BottomNavTab
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NavigationViewModel(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val connectivityMonitor: ConnectivityMonitor,
    private val syncEngine: SyncEngine,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val biosRepository: BiosRepository? = null,
) {
    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val sections = listOf(
        SpScreen.Home, SpScreen.Explore, SpScreen.Consoles, SpScreen.Collections, SpScreen.Activity, SpScreen.Settings
    )

    init {
        restoreSession()
        observeAuthState()
    }

    private fun observeAuthState() {
        scope.launch(dispatchers.main) {
            connectivityMonitor.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.AuthFailed -> {
                        _state.update {
                            it.copy(
                                currentScreen = SpScreen.Login,
                                backStack = emptyList(),
                                showInGameOverlay = false,
                            )
                        }
                    }
                    else -> { /* handled by UI components */ }
                }
            }
        }
    }

    fun onIntent(intent: NavigationIntent) {
        when (intent) {
            is NavigationIntent.NavigateTo -> {
                _state.update { current ->
                    current.copy(
                        currentScreen = intent.screen,
                        backStack = current.backStack + current.currentScreen,
                        isGoingBack = false,
                        isTabSwitch = false,
                    )
                }
            }

            is NavigationIntent.SwitchTab -> {
                _state.update { current ->
                    current.copy(
                        currentScreen = intent.screen,
                        backStack = emptyList(),
                        isGoingBack = false,
                        isTabSwitch = true,
                    )
                }
            }

            NavigationIntent.GoBack -> {
                _state.update { current ->
                    if (current.backStack.isNotEmpty()) {
                        current.copy(
                            currentScreen = current.backStack.last(),
                            backStack = current.backStack.dropLast(1),
                            isGoingBack = true,
                            isTabSwitch = false,
                        )
                    } else if (current.currentScreen is SpScreen.Settings ||
                        current.currentScreen is SpScreen.Downloads ||
                        current.currentScreen is SpScreen.Explore ||
                        current.currentScreen is SpScreen.Consoles ||
                        current.currentScreen is SpScreen.Collections ||
                        current.currentScreen is SpScreen.Activity
                    ) {
                        // When on a non-Home tab with empty back stack, return to Home
                        current.copy(
                            currentScreen = SpScreen.Home,
                            isGoingBack = true,
                            isTabSwitch = false,
                        )
                    } else {
                        current
                    }
                }
            }

            NavigationIntent.NextSection -> {
                _state.update { current ->
                    val currentTab = activeTabForScreen(current.currentScreen)
                    val currentIndex = BottomNavTab.entries.indexOf(currentTab)
                    val nextIndex = (currentIndex + 1) % sections.size
                    current.copy(
                        currentScreen = sections[nextIndex],
                        backStack = emptyList(),
                        isGoingBack = false,
                        isTabSwitch = false,
                    )
                }
            }

            NavigationIntent.PreviousSection -> {
                _state.update { current ->
                    val currentTab = activeTabForScreen(current.currentScreen)
                    val currentIndex = BottomNavTab.entries.indexOf(currentTab)
                    val prevIndex = (currentIndex - 1 + sections.size) % sections.size
                    current.copy(
                        currentScreen = sections[prevIndex],
                        backStack = emptyList(),
                        isGoingBack = true,
                        isTabSwitch = false,
                    )
                }
            }

            is NavigationIntent.ShowOverlay -> {
                _state.update {
                    it.copy(
                        showInGameOverlay = true,
                        overlayGameId = intent.gameId,
                        overlaySharedSessionId = intent.sharedSessionId,
                        overlayTurnToken = intent.turnToken,
                        overlayNetplaySessionId = intent.netplaySessionId,
                        overlayNetplayLocalPort = intent.netplayLocalPort,
                        overlayNetplayInputDelay = intent.netplayInputDelay,
                        overlayNetplayIsHost = intent.netplayIsHost,
                        overlayChallengeId = intent.challengeId,
                        overlaySkipAutoLoad = intent.skipAutoLoad,
                        overlaySessionId = intent.sessionId,
                        screenBehindOverlay = it.currentScreen,
                        backStackBehindOverlay = it.backStack,
                    )
                }
            }

            NavigationIntent.HideOverlay -> {
                _state.update {
                    if (it.screenBehindOverlay != null) {
                        it.copy(
                            showInGameOverlay = false,
                            overlayGameId = null,
                            overlaySharedSessionId = null,
                            overlayTurnToken = null,
                            overlayNetplaySessionId = null,
                            overlayNetplayLocalPort = 0,
                            overlayNetplayInputDelay = 3,
                            overlayNetplayIsHost = false,
                            overlayChallengeId = null,
                            overlaySkipAutoLoad = false,
                            overlaySessionId = null,
                            currentScreen = it.screenBehindOverlay,
                            backStack = it.backStackBehindOverlay,
                            screenBehindOverlay = null,
                            backStackBehindOverlay = emptyList(),
                        )
                    } else {
                        it.copy(
                            showInGameOverlay = false,
                            overlayGameId = null,
                            overlaySharedSessionId = null,
                            overlayTurnToken = null,
                            overlayNetplaySessionId = null,
                            overlayNetplayLocalPort = 0,
                            overlayNetplayInputDelay = 3,
                            overlayNetplayIsHost = false,
                            overlayChallengeId = null,
                            overlaySkipAutoLoad = false,
                            overlaySessionId = null,
                        )
                    }
                }
                // Sync pending save states to the server after leaving a game.
                // Auto-save writes to local storage only (fast), so the upload
                // happens here in the background.
                scope.launch(dispatchers.io) { syncEngine.syncAll() }
            }

        }
    }

    companion object {
        fun activeTabForScreen(screen: SpScreen): BottomNavTab = when (screen) {
            is SpScreen.Explore,
            is SpScreen.ExploreTheme,
            is SpScreen.ExploreKeyword,
            is SpScreen.ExploreSeries,
            is SpScreen.ExploreFranchise,
            is SpScreen.ExploreMood,
            is SpScreen.ExploreDeveloper,
            is SpScreen.ExplorePublisher,
            is SpScreen.DeveloperGames,
            -> BottomNavTab.EXPLORE
            is SpScreen.Consoles, is SpScreen.Console -> BottomNavTab.CONSOLES
            is SpScreen.Collections, is SpScreen.CollectionDetail -> BottomNavTab.COLLECTIONS
            is SpScreen.Activity, is SpScreen.Stats -> BottomNavTab.ACTIVITY
            is SpScreen.Settings, is SpScreen.ConsoleSettings, is SpScreen.Licenses -> BottomNavTab.SETTINGS
            else -> BottomNavTab.HOME
        }
    }

    fun resetDatabase() {
        DatabaseResetHelper.resetDatabase()
        _state.update {
            it.copy(
                currentScreen = SpScreen.ServerConnection,
                backStack = emptyList(),
                showInGameOverlay = false,
            )
        }
    }

    private fun restoreSession() {
        scope.launch(dispatchers.io) {
            // Check for database errors before restoring session
            val dbError = DatabaseHealthCheck.error.value
            if (dbError != null) {
                connectivityMonitor.reportDatabaseError(dbError)
            }

            val result = restoreSessionUseCase()
            val screen = when (result) {
                RestoreSessionResult.Success -> SpScreen.Home
                RestoreSessionResult.OfflineSuccess -> SpScreen.Home
                is RestoreSessionResult.NeedsLogin -> SpScreen.Login
                RestoreSessionResult.NoSession -> SpScreen.ServerConnection
            }
            val serverUrl = when (result) {
                is RestoreSessionResult.NeedsLogin -> result.serverUrl
                else -> null
            }
            _state.update {
                it.copy(
                    currentScreen = screen,
                    isRestoringSession = false,
                    restoredServerUrl = serverUrl,
                    isOffline = result is RestoreSessionResult.OfflineSuccess,
                )
            }

            // Start connectivity monitoring and sync engine after successful session restore
            if (result is RestoreSessionResult.Success || result is RestoreSessionResult.OfflineSuccess) {
                connectivityMonitor.start()
                syncEngine.start()

                // Sync BIOS files in background (AC 4.1)
                biosRepository?.let { repo ->
                    scope.launch(dispatchers.io) {
                        try {
                            repo.syncBiosFiles()
                            repo.fetchBiosStatus()
                        } catch (e: Exception) {
                            println("[Bios] Background sync failed: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
