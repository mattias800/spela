package com.spela.player.presentation.state

import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game

data class GameListState(
    val consoles: List<Console> = emptyList(),
    val games: List<Game> = emptyList(),
    val recentGames: List<Game> = emptyList(),
    val favoriteGames: List<Game> = emptyList(),
    val playLaterGames: List<Game> = emptyList(),
    val selectedConsoleId: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)
