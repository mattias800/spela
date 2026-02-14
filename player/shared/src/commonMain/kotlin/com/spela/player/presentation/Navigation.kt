package com.spela.player.presentation

/**
 * Navigation destinations for the Spela player app.
 * Used by the UI layer to manage screen transitions.
 */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object ConsoleBrowser : Screen("consoles")
    data class GameList(val consoleId: String) : Screen("consoles/$consoleId/games")
    data class GameDetail(val gameId: String) : Screen("games/$gameId")
    data class Emulation(val gameId: String) : Screen("play/$gameId")
    data object Settings : Screen("settings")
    data object Downloads : Screen("downloads")
    data class UserProfile(val userId: String) : Screen("users/$userId")
}

/**
 * Simple navigation state holder.
 * The UI layer can use this or implement its own navigation solution.
 */
class NavigationState {
    private val backStack = mutableListOf<Screen>(Screen.Login)

    val currentScreen: Screen
        get() = backStack.last()

    fun navigateTo(screen: Screen) {
        backStack.add(screen)
    }

    fun goBack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeLast()
        return true
    }

    fun popToRoot() {
        val root = backStack.first()
        backStack.clear()
        backStack.add(root)
    }

    fun replaceWith(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }
}
