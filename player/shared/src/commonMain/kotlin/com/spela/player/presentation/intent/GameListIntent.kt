package com.spela.player.presentation.intent

sealed interface GameListIntent {
    data object LoadDashboard : GameListIntent
    data object LoadConsoles : GameListIntent
    data class SelectConsole(val consoleId: String) : GameListIntent
    data class Search(val query: String) : GameListIntent
    data class ToggleFavorite(val gameId: String, val isFavorite: Boolean) : GameListIntent
    data class TogglePlayLater(val gameId: String, val isInPlayLater: Boolean) : GameListIntent
    data object DismissError : GameListIntent
}
