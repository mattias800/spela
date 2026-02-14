package com.spela.player.presentation.intent

sealed interface GameDetailIntent {
    data class LoadGame(val gameId: String) : GameDetailIntent
    data object DownloadGame : GameDetailIntent
    data object PlayGame : GameDetailIntent
    data object DeleteLocalGame : GameDetailIntent
    data object ToggleFavorite : GameDetailIntent
    data class RateGame(val rating: Int, val review: String = "") : GameDetailIntent
    data object DeleteRating : GameDetailIntent
    data object LoadSharedSaves : GameDetailIntent
    data class ShareSave(val saveId: String, val name: String, val description: String) : GameDetailIntent
    data class DownloadSharedSave(val saveId: String) : GameDetailIntent
    data class DeleteSharedSave(val saveId: String) : GameDetailIntent
    data object DismissError : GameDetailIntent
}
