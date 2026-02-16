package com.spela.player.presentation.state

import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MostPlayedGame

data class StatsState(
    val mostPlayedGames: List<MostPlayedGame> = emptyList(),
    val activePlayers: List<ActivePlayer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
