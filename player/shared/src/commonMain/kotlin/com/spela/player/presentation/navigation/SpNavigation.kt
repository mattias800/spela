package com.spela.player.presentation.navigation

sealed class SpScreen(val route: String) {
    data object ServerConnection : SpScreen("server_connection")
    data object Login : SpScreen("login")
    data object Home : SpScreen("home")
    data class Console(val consoleId: String) : SpScreen("console/$consoleId")
    data class GameDetail(val gameId: String) : SpScreen("game/$gameId")
    data object Downloads : SpScreen("downloads")
    data object Settings : SpScreen("settings")
}

data class NavigationState(
    val currentScreen: SpScreen = SpScreen.ServerConnection,
    val backStack: List<SpScreen> = emptyList(),
    val showInGameOverlay: Boolean = false,
    val overlayGameId: String? = null,
    val isRestoringSession: Boolean = true,
)

sealed interface NavigationIntent {
    data class NavigateTo(val screen: SpScreen) : NavigationIntent
    data object GoBack : NavigationIntent
    data class ShowOverlay(val gameId: String) : NavigationIntent
    data object HideOverlay : NavigationIntent
    data class SwitchTab(val route: String) : NavigationIntent
}
