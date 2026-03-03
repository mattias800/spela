package com.spela.player.presentation.state

data class GameSyncState(
    val gameId: String,
    val message: String,
    val isTimedOut: Boolean = false,
)
