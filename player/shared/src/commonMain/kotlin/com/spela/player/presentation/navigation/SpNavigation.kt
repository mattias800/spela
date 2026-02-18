package com.spela.player.presentation.navigation

sealed class SpScreen(val route: String) {
    data object ServerConnection : SpScreen("server_connection")
    data object Login : SpScreen("login")
    data object Home : SpScreen("home")
    data object Library : SpScreen("library")
    data object AllGames : SpScreen("all_games")
    data object Favorites : SpScreen("favorites")
    data object PlayLater : SpScreen("play_later")
    data object Collections : SpScreen("collections")
    data class CollectionDetail(val collectionId: String) : SpScreen("collection/$collectionId")
    data object Stats : SpScreen("stats")
    data object Activity : SpScreen("activity")
    data class Console(val consoleId: String) : SpScreen("console/$consoleId")
    data class GameDetail(val gameId: String) : SpScreen("game/$gameId")
    data object Downloads : SpScreen("downloads")
    data object Settings : SpScreen("settings")
    data class ConsoleSettings(val consoleId: String) : SpScreen("console_settings/$consoleId")
    data class UserProfile(val userId: String) : SpScreen("user/$userId")
    data object Relays : SpScreen("relays")
    data class RelayDetail(val relayId: String) : SpScreen("relay/$relayId")
    data object NetplaySessions : SpScreen("netplay")
    data class NetplayLobby(val sessionId: String) : SpScreen("netplay/$sessionId")
    data object Licenses : SpScreen("licenses")
    data class ChallengeList(val gameId: String, val gameTitle: String) : SpScreen("challenges/$gameId")
    data class ChallengeDetail(val challengeId: String) : SpScreen("challenge/$challengeId")
}

data class NavigationState(
    val currentScreen: SpScreen = SpScreen.ServerConnection,
    val backStack: List<SpScreen> = emptyList(),
    val isGoingBack: Boolean = false,
    val showInGameOverlay: Boolean = false,
    val overlayGameId: String? = null,
    val overlayRelayId: String? = null,
    val overlayTurnToken: String? = null,
    val overlayNetplaySessionId: String? = null,
    val overlayNetplayLocalPort: Int = 0,
    val overlayNetplayInputDelay: Int = 3,
    val overlayNetplayIsHost: Boolean = false,
    val overlayChallengeId: String? = null,
    val overlaySkipAutoLoad: Boolean = false,
    val screenBehindOverlay: SpScreen? = null,
    val backStackBehindOverlay: List<SpScreen> = emptyList(),
    val isRestoringSession: Boolean = true,
    val restoredServerUrl: String? = null,
)

sealed interface NavigationIntent {
    data class NavigateTo(val screen: SpScreen) : NavigationIntent
    data object GoBack : NavigationIntent
    data class ShowOverlay(
        val gameId: String,
        val relayId: String? = null,
        val turnToken: String? = null,
        val netplaySessionId: String? = null,
        val netplayLocalPort: Int = 0,
        val netplayInputDelay: Int = 3,
        val netplayIsHost: Boolean = false,
        val challengeId: String? = null,
        val skipAutoLoad: Boolean = false,
    ) : NavigationIntent
    data object HideOverlay : NavigationIntent
}
