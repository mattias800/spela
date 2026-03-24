package com.spela.player.presentation.navigation

sealed class SpScreen(val route: String) {
    data object ServerConnection : SpScreen("server_connection")
    data object Login : SpScreen("login")
    data object Home : SpScreen("home")
    data object Explore : SpScreen("explore")
    data object Consoles : SpScreen("consoles")
    data object AllGames : SpScreen("all_games")
    data object Favorites : SpScreen("favorites")
    data object PlayLater : SpScreen("play_later")
    data object Collections : SpScreen("collections")
    data class CollectionDetail(val collectionId: String) : SpScreen("collection/$collectionId")
    data object Stats : SpScreen("stats")
    data object Activity : SpScreen("activity")
    data class Console(val consoleId: String) : SpScreen("console/$consoleId")
    data class ConsoleGames(val consoleId: String) : SpScreen("console_games/$consoleId")
    data class GameDetail(val gameId: String) : SpScreen("game/$gameId")
    data object Downloads : SpScreen("downloads")
    data object Settings : SpScreen("settings")
    data class ConsoleSettings(val consoleId: String) : SpScreen("console_settings/$consoleId")
    data class UserProfile(val userId: String) : SpScreen("user/$userId")
    data object SharedSessions : SpScreen("sharedSessions")
    data class SharedSessionDetail(val sharedSessionId: String) : SpScreen("sharedSession/$sharedSessionId")
    data object NetplaySessions : SpScreen("netplay")
    data class NetplayLobby(val sessionId: String) : SpScreen("netplay/$sessionId")
    data object Licenses : SpScreen("licenses")
    data object GlobalChallenges : SpScreen("challenges")
    data class ChallengeList(val gameId: String, val gameTitle: String) : SpScreen("challenges/$gameId")
    data class ChallengeDetail(val challengeId: String) : SpScreen("challenge/$challengeId")
    data class SessionDetail(val sessionId: String) : SpScreen("sessions/$sessionId")
    data object TopLists : SpScreen("top_lists")
    data class ExploreTheme(val themeId: String, val themeName: String) : SpScreen("explore_theme/$themeId")
    data class ExploreKeyword(val keywordId: String, val keywordName: String) : SpScreen("explore_keyword/$keywordId")
    data class ExploreSeries(val seriesId: String, val seriesName: String) : SpScreen("explore_series/$seriesId")
    data class ExploreFranchise(val franchiseId: String, val franchiseName: String) : SpScreen("explore_franchise/$franchiseId")

    data class ExploreMood(val moodId: String, val moodName: String) : SpScreen("explore_mood/$moodId")
    data class ExploreDeveloper(val name: String) : SpScreen("explore_developer/$name")
    data class ExplorePublisher(val name: String) : SpScreen("explore_publisher/$name")
    data class DeveloperGames(val name: String, val isDeveloper: Boolean = true) : SpScreen("developer_games/$name/$isDeveloper")
    data object ExploreGallery : SpScreen("explore_gallery")
    data object ExploreSearch : SpScreen("explore_search")
    data object ExploreWizard : SpScreen("explore_wizard")
    data object GlobalSearch : SpScreen("global_search")
    data class GameAchievements(val gameId: String) : SpScreen("game_achievements/$gameId")
}

data class NavigationState(
    val currentScreen: SpScreen = SpScreen.ServerConnection,
    val backStack: List<SpScreen> = emptyList(),
    val isGoingBack: Boolean = false,
    val isTabSwitch: Boolean = false,
    val showInGameOverlay: Boolean = false,
    val overlayGameId: String? = null,
    val overlaySharedSessionId: String? = null,
    val overlayTurnToken: String? = null,
    val overlayNetplaySessionId: String? = null,
    val overlayNetplayLocalPort: Int = 0,
    val overlayNetplayInputDelay: Int = 3,
    val overlayNetplayIsHost: Boolean = false,
    val overlayChallengeId: String? = null,
    val overlaySkipAutoLoad: Boolean = false,
    val overlaySessionId: String? = null,
    val screenBehindOverlay: SpScreen? = null,
    val backStackBehindOverlay: List<SpScreen> = emptyList(),
    val isRestoringSession: Boolean = true,
    val restoredServerUrl: String? = null,
    val isOffline: Boolean = false,
)

sealed interface NavigationIntent {
    data class NavigateTo(val screen: SpScreen) : NavigationIntent
    /** Switch to a root tab via bottom nav click — no animation. */
    data class SwitchTab(val screen: SpScreen) : NavigationIntent
    data object GoBack : NavigationIntent
    data object NextSection : NavigationIntent
    data object PreviousSection : NavigationIntent
    data class ShowOverlay(
        val gameId: String,
        val sharedSessionId: String? = null,
        val turnToken: String? = null,
        val netplaySessionId: String? = null,
        val netplayLocalPort: Int = 0,
        val netplayInputDelay: Int = 3,
        val netplayIsHost: Boolean = false,
        val challengeId: String? = null,
        val skipAutoLoad: Boolean = false,
        val sessionId: String? = null,
    ) : NavigationIntent
    data object HideOverlay : NavigationIntent
}
