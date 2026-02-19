package com.spela.player.presentation.state

import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.RatingSummary
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SharedSaveState

data class GameDetailState(
    val gameDetail: GameDetail? = null,
    val saveStates: List<SaveState> = emptyList(),
    val sharedSaves: List<SharedSaveState> = emptyList(),
    val downloadProgress: DownloadProgress? = null,
    val isGameCached: Boolean = false,
    val isLoading: Boolean = false,
    val isScraping: Boolean = false,
    val isSharing: Boolean = false,
    val myRating: Int? = null,
    val ratingSummary: RatingSummary? = null,
    val isRating: Boolean = false,
    val showAddToCollectionDialog: Boolean = false,
    val userCollections: List<GameCollection> = emptyList(),
    val isLoadingCollections: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
)
