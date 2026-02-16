package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameCollectionDetail
import com.spela.player.domain.usecase.GetCollectionDetailUseCase
import com.spela.player.domain.usecase.GetMyCollectionsUseCase
import com.spela.player.domain.usecase.GetPublicCollectionsUseCase
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionsState(
    val myCollections: List<GameCollection> = emptyList(),
    val publicCollections: List<GameCollection> = emptyList(),
    val selectedDetail: GameCollectionDetail? = null,
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val error: String? = null,
)

sealed interface CollectionsIntent {
    data object LoadMyCollections : CollectionsIntent
    data object LoadPublicCollections : CollectionsIntent
    data class LoadCollectionDetail(val collectionId: String) : CollectionsIntent
    data object DismissError : CollectionsIntent
}

class CollectionsViewModel(
    private val getMyCollectionsUseCase: GetMyCollectionsUseCase,
    private val getPublicCollectionsUseCase: GetPublicCollectionsUseCase,
    private val getCollectionDetailUseCase: GetCollectionDetailUseCase,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CollectionsState())
    val state: StateFlow<CollectionsState> = _state.asStateFlow()

    fun onIntent(intent: CollectionsIntent) {
        when (intent) {
            CollectionsIntent.LoadMyCollections -> loadMyCollections()
            CollectionsIntent.LoadPublicCollections -> loadPublicCollections()
            is CollectionsIntent.LoadCollectionDetail -> loadDetail(intent.collectionId)
            CollectionsIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadMyCollections() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            getMyCollectionsUseCase().fold(
                onSuccess = { collections ->
                    _state.update { it.copy(myCollections = collections, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun loadPublicCollections() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            getPublicCollectionsUseCase().fold(
                onSuccess = { collections ->
                    _state.update { it.copy(publicCollections = collections, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun loadDetail(collectionId: String) {
        _state.update { it.copy(isDetailLoading = true, selectedDetail = null) }
        scope.launch(dispatchers.io) {
            getCollectionDetailUseCase(collectionId).fold(
                onSuccess = { detail ->
                    _state.update { it.copy(selectedDetail = detail, isDetailLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isDetailLoading = false) }
                },
            )
        }
    }
}
