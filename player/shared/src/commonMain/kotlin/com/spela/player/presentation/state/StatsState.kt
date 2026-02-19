package com.spela.player.presentation.state

import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.domain.model.UserStats

data class StatsState(
    val mostPlayedGames: List<MostPlayedGame> = emptyList(),
    val activePlayers: List<ActivePlayer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val personalStats: UserStats? = null,
    val isLoadingPersonalStats: Boolean = false,
)
