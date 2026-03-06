package com.spela.player.presentation.state

import com.spela.player.domain.model.TopListGame

data class TopListsState(
    val games: List<TopListGame> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
