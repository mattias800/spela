package com.spela.player.presentation.state

import com.spela.player.domain.model.LongestGame
import com.spela.player.domain.model.TopListGame
import com.spela.player.domain.model.TopListTab

data class TopListsState(
    val selectedTab: TopListTab = TopListTab.TOP_RATED,
    val games: List<TopListGame> = emptyList(),
    val longestGames: List<LongestGame> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
