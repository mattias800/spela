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
    private val coreUpdateService: com.spela.player.data.repository.CoreUpdateService? = null,
) {
    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val sections = listOf(
        SpScreen.Home, SpScreen.Explore, SpScreen.Consoles, SpScreen.ConnectedServers,
        SpScreen.Collections, SpScreen.Activity, SpScreen.Settings
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
                                activeTab = BottomNavTab.HOME,
                                tabStacks = defaultTabStacks(),
                                showInGameOverlay = false,
                            )
                        }
                    }
                    else -> { /* handled by UI components */ }
                }
            }
        }
    }

    /** Push onto the active tab's stack. */
    private fun pushScreen(state: NavigationState, screen: SpScreen): NavigationState {
        val currentStack = state.tabStacks[state.activeTab] ?: listOf()
        return state.copy(
            tabStacks = state.tabStacks + (state.activeTab to currentStack + screen),
            isGoingBack = false,
            isTabSwitch = false,
        )
    }

    /** Pop from the active tab's stack. Returns null if nothing to pop. */
    private fun popScreen(state: NavigationState): NavigationState? {
        val currentStack = state.tabStacks[state.activeTab] ?: return null
        if (currentStack.size > 1) {
            return state.copy(
                tabStacks = state.tabStacks + (state.activeTab to currentStack.dropLast(1)),
                isGoingBack = true,
                isTabSwitch = false,
            )
        }
        // Stack has only the root — nothing to pop. Back does nothing at a tab
        // root: it never leaves the current tab or jumps to Home (#1372). Each
        // tab owns its own stack; that tab's root is the floor.
        return null
    }

    fun onIntent(intent: NavigationIntent) {
        when (intent) {
            is NavigationIntent.NavigateTo -> {
                _state.update { pushScreen(it, intent.screen) }
            }

            is NavigationIntent.SwitchTab -> {
                val targetTab = tabForRootScreen(intent.screen) ?: BottomNavTab.HOME
                _state.update { current ->
                    if (targetTab == current.activeTab) {
                        // Re-tap on the active tab. Universal "take me back to
                        // the root of this section" gesture (matches iOS, X,
                        // Bluesky, Instagram). Pop this tab's stack to its
                        // root unless it's already there — see #846.
                        val rootStack = listOf(intent.screen)
                        val currentStack = current.tabStacks[targetTab]
                        if (currentStack == rootStack) {
                            current
                        } else {
                            current.copy(
                                tabStacks = current.tabStacks + (targetTab to rootStack),
                                isGoingBack = true,
                                isTabSwitch = false,
                            )
                        }
                    } else {
                        current.copy(
                            activeTab = targetTab,
                            isGoingBack = false,
                            isTabSwitch = true,
                        )
                    }
                }
            }

            NavigationIntent.ResetToHome -> {
                _state.update {
                    it.copy(
                        activeTab = BottomNavTab.HOME,
                        tabStacks = defaultTabStacks(),
                        isGoingBack = false,
                        isTabSwitch = false,
                        showInGameOverlay = false,
                        activeTabBehindOverlay = null,
                        tabStacksBehindOverlay = emptyMap(),
                    )
                }
            }

            NavigationIntent.GoBack -> {
                _state.update { current ->
                    popScreen(current) ?: current
                }
            }

            NavigationIntent.NextSection -> {
                _state.update { current ->
                    val currentIndex = BottomNavTab.entries.indexOf(current.activeTab)
                    val nextIndex = (currentIndex + 1) % sections.size
                    current.copy(
                        activeTab = BottomNavTab.entries[nextIndex],
                        isGoingBack = false,
                        isTabSwitch = true,
                    )
                }
            }

            NavigationIntent.PreviousSection -> {
                _state.update { current ->
                    val currentIndex = BottomNavTab.entries.indexOf(current.activeTab)
                    val prevIndex = (currentIndex - 1 + sections.size) % sections.size
                    current.copy(
                        activeTab = BottomNavTab.entries[prevIndex],
                        isGoingBack = true,
                        isTabSwitch = true,
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
                        overlayForceNewSession = intent.forceNewSession,
                        overlaySessionId = intent.sessionId,
                        activeTabBehindOverlay = it.activeTab,
                        tabStacksBehindOverlay = it.tabStacks,
                    )
                }
            }

            NavigationIntent.HideOverlay -> {
                _state.update {
                    val restoredTab = it.activeTabBehindOverlay ?: it.activeTab
                    val restoredStacks = it.tabStacksBehindOverlay.ifEmpty { it.tabStacks }
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
                        overlayForceNewSession = false,
                        overlaySessionId = null,
                        activeTab = restoredTab,
                        tabStacks = restoredStacks,
                        activeTabBehindOverlay = null,
                        tabStacksBehindOverlay = emptyMap(),
                        overlayClosedTick = it.overlayClosedTick + 1,
                    )
                }
                // Sync pending save states to the server after leaving a game.
                scope.launch(dispatchers.io) { syncEngine.syncAll() }
            }

        }
    }

    fun resetDatabase() {
        DatabaseResetHelper.resetDatabase()
        _state.update {
            it.copy(
                activeTab = BottomNavTab.HOME,
                tabStacks = defaultTabStacks(),
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
                when (screen) {
                    // Auth screens bypass the tab system
                    SpScreen.Login, SpScreen.ServerConnection -> it.copy(
                        activeTab = BottomNavTab.HOME,
                        tabStacks = defaultTabStacks().toMutableMap().apply {
                            put(BottomNavTab.HOME, listOf(screen))
                        },
                        isRestoringSession = false,
                        restoredServerUrl = serverUrl,
                        isOffline = result is RestoreSessionResult.OfflineSuccess,
                    )
                    else -> it.copy(
                        activeTab = BottomNavTab.HOME,
                        tabStacks = defaultTabStacks(),
                        isRestoringSession = false,
                        restoredServerUrl = serverUrl,
                        isOffline = result is RestoreSessionResult.OfflineSuccess,
                    )
                }
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

                // Background prefetch of stale cached cores — keeps the
                // user off the foreground download path when buildbot
                // has rolled out a new nightly since they last played.
                // The service single-flights internally so re-calls per
                // session no-op. See #1192.
                coreUpdateService?.prefetchStaleCachedCores()
            }
        }
    }

    companion object {
        /** Maps a tab root screen to its BottomNavTab. */
        private fun tabForRootScreen(screen: SpScreen): BottomNavTab? = when (screen) {
            SpScreen.Home -> BottomNavTab.HOME
            SpScreen.Explore -> BottomNavTab.EXPLORE
            SpScreen.Consoles -> BottomNavTab.CONSOLES
            SpScreen.ConnectedServers -> BottomNavTab.CONNECTED_SERVERS
            SpScreen.Collections -> BottomNavTab.COLLECTIONS
            SpScreen.Activity -> BottomNavTab.ACTIVITY
            SpScreen.Settings -> BottomNavTab.SETTINGS
            else -> null
        }
    }
}
