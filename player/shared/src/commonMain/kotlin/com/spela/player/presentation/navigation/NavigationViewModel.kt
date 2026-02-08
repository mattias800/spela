package com.spela.player.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NavigationViewModel {
    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    fun onIntent(intent: NavigationIntent) {
        when (intent) {
            is NavigationIntent.NavigateTo -> {
                _state.update { current ->
                    current.copy(
                        currentScreen = intent.screen,
                        backStack = current.backStack + current.currentScreen,
                    )
                }
            }

            NavigationIntent.GoBack -> {
                _state.update { current ->
                    if (current.backStack.isNotEmpty()) {
                        current.copy(
                            currentScreen = current.backStack.last(),
                            backStack = current.backStack.dropLast(1),
                        )
                    } else {
                        current
                    }
                }
            }

            is NavigationIntent.ShowOverlay -> {
                _state.update {
                    it.copy(showInGameOverlay = true, overlayGameId = intent.gameId)
                }
            }

            NavigationIntent.HideOverlay -> {
                _state.update {
                    it.copy(showInGameOverlay = false, overlayGameId = null)
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
                    )
                }
            }
        }
    }
}
