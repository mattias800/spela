package com.spela.player.presentation.intent

sealed interface GameDetailIntent {
    data class LoadGame(val gameId: String) : GameDetailIntent
    data object DownloadGame : GameDetailIntent
    data object PlayGame : GameDetailIntent
    data object DeleteLocalGame : GameDetailIntent
    data object ToggleFavorite : GameDetailIntent
    data object DismissError : GameDetailIntent
}
