package com.spela.player.presentation.navigation

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.SyncEngine
import com.spela.player.data.remote.interceptor.AuthEvent
import com.spela.player.data.remote.interceptor.AuthEventBus
import com.spela.player.data.repository.BiosRepository
import com.spela.player.domain.usecase.RestoreSessionResult
import com.spela.player.domain.usecase.RestoreSessionUseCase
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
    private val authEventBus: AuthEventBus,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
    private val biosRepository: BiosRepository? = null,
) {
    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    init {
        restoreSession()
        observeAuthEvents()
    }

    private fun observeAuthEvents() {
        scope.launch(dispatchers.main) {
            authEventBus.events.collect { event ->
                when (event) {
                    AuthEvent.SessionExpired -> {
                        _state.update {
                            it.copy(
                                currentScreen = SpScreen.Login,
                                backStack = emptyList(),
                                showInGameOverlay = false,
                            )
                        }
                    }
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
                        )
                    } else if (current.currentScreen is SpScreen.Settings ||
                        current.currentScreen is SpScreen.Downloads ||
                        current.currentScreen is SpScreen.Library ||
                        current.currentScreen is SpScreen.Activity
                    ) {
                        // When on a non-Home tab with empty back stack, return to Home
                        current.copy(
                            currentScreen = SpScreen.Home,
                            isGoingBack = true,
                        )
                    } else {
                        current
                    }
                }
            }

            is NavigationIntent.ShowOverlay -> {
                _state.update {
                    it.copy(
                        showInGameOverlay = true,
                        overlayGameId = intent.gameId,
                        overlayRelayId = intent.relayId,
                        overlayTurnToken = intent.turnToken,
                        overlayNetplaySessionId = intent.netplaySessionId,
                        overlayNetplayLocalPort = intent.netplayLocalPort,
                        overlayNetplayInputDelay = intent.netplayInputDelay,
                        overlayNetplayIsHost = intent.netplayIsHost,
                        overlayChallengeId = intent.challengeId,
                        overlaySkipAutoLoad = intent.skipAutoLoad,
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
                            overlayRelayId = null,
                            overlayTurnToken = null,
                            overlayNetplaySessionId = null,
                            overlayNetplayLocalPort = 0,
                            overlayNetplayInputDelay = 3,
                            overlayNetplayIsHost = false,
                            overlayChallengeId = null,
                            overlaySkipAutoLoad = false,
                            currentScreen = it.screenBehindOverlay,
                            backStack = it.backStackBehindOverlay,
                            screenBehindOverlay = null,
                            backStackBehindOverlay = emptyList(),
                        )
                    } else {
                        it.copy(
                            showInGameOverlay = false,
                            overlayGameId = null,
                            overlayRelayId = null,
                            overlayTurnToken = null,
                            overlayNetplaySessionId = null,
                            overlayNetplayLocalPort = 0,
                            overlayNetplayInputDelay = 3,
                            overlayNetplayIsHost = false,
                            overlayChallengeId = null,
                            overlaySkipAutoLoad = false,
                        )
                    }
                }
            }

        }
    }

    private fun restoreSession() {
        scope.launch(dispatchers.io) {
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
