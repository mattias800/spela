package com.spela.player.presentation.navigation

sealed class SpScreen(val route: String) {
    data object ServerConnection : SpScreen("server_connection")
    data object Login : SpScreen("login")
    data object Home : SpScreen("home")
    data class Console(val consoleId: String) : SpScreen("console/$consoleId")
    data class GameDetail(val gameId: String) : SpScreen("game/$gameId")
    data object Downloads : SpScreen("downloads")
    data object Settings : SpScreen("settings")
    data class ConsoleSettings(val consoleId: String) : SpScreen("console_settings/$consoleId")
}

data class NavigationState(
    val currentScreen: SpScreen = SpScreen.ServerConnection,
    val backStack: List<SpScreen> = emptyList(),
    val isGoingBack: Boolean = false,
    val showInGameOverlay: Boolean = false,
    val overlayGameId: String? = null,
    val screenBehindOverlay: SpScreen? = null,
    val backStackBehindOverlay: List<SpScreen> = emptyList(),
    val isRestoringSession: Boolean = true,
    val restoredServerUrl: String? = null,
)

sealed interface NavigationIntent {
    data class NavigateTo(val screen: SpScreen) : NavigationIntent
    data object GoBack : NavigationIntent
    data class ShowOverlay(val gameId: String) : NavigationIntent
    data object HideOverlay : NavigationIntent
}
