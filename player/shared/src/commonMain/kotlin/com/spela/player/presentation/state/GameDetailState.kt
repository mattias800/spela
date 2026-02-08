package com.spela.player.presentation.state

import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.SaveState

data class GameDetailState(
    val gameDetail: GameDetail? = null,
    val saveStates: List<SaveState> = emptyList(),
    val downloadProgress: DownloadProgress? = null,
    val isGameCached: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
