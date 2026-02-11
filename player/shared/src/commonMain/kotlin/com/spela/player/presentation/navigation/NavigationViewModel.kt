package com.spela.player.presentation.navigation

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
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    init {
        restoreSession()
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
                        current.currentScreen is SpScreen.Downloads
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
                            currentScreen = it.screenBehindOverlay,
                            backStack = it.backStackBehindOverlay,
                            screenBehindOverlay = null,
                            backStackBehindOverlay = emptyList(),
                        )
                    } else {
                        it.copy(
                            showInGameOverlay = false,
                            overlayGameId = null,
                        )
                    }
                }
            }

            is NavigationIntent.SwitchTab -> {
                val screen = when (intent.route) {
                    "home" -> SpScreen.Home
                    "downloads" -> SpScreen.Downloads
                    "settings" -> SpScreen.Settings
                    else -> return
                }
                _state.update {
                    it.copy(
                        currentScreen = screen,
                        backStack = emptyList(),
                        isGoingBack = false,
                    )
                }
            }
        }
    }

    private fun restoreSession() {
        scope.launch(dispatchers.io) {
            val result = restoreSessionUseCase()
            val screen = when (result) {
                RestoreSessionResult.Success -> SpScreen.Home
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
                )
            }
        }
    }
}
