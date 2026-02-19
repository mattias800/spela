package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ScrapeService
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameCollection
import com.spela.player.domain.model.GameCollectionDetail
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.usecase.CreateCollectionUseCase
import com.spela.player.domain.usecase.DeleteCollectionUseCase
import com.spela.player.domain.usecase.GetCollectionDetailUseCase
import com.spela.player.domain.usecase.GetMyCollectionsUseCase
import com.spela.player.domain.usecase.GetPublicCollectionsUseCase
import com.spela.player.domain.usecase.RemoveGameFromCollectionUseCase
import com.spela.player.domain.usecase.UpdateCollectionUseCase
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
    val currentUserId: String = "",
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val isCreating: Boolean = false,
    val isUpdating: Boolean = false,
    val isDeleting: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val collectionDeleted: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
)

sealed interface CollectionsIntent {
    data object LoadMyCollections : CollectionsIntent
    data object LoadPublicCollections : CollectionsIntent
    data class LoadCollectionDetail(val collectionId: String) : CollectionsIntent

    // Create
    data object ShowCreateDialog : CollectionsIntent
    data object DismissCreateDialog : CollectionsIntent
    data class CreateCollection(
        val name: String,
        val description: String?,
        val isPublic: Boolean,
    ) : CollectionsIntent

    // Edit
    data object ShowEditDialog : CollectionsIntent
    data object DismissEditDialog : CollectionsIntent
    data class UpdateCollection(
        val name: String?,
        val description: String?,
        val isPublic: Boolean?,
    ) : CollectionsIntent

    // Delete
    data object ShowDeleteDialog : CollectionsIntent
    data object DismissDeleteDialog : CollectionsIntent
    data object DeleteCollection : CollectionsIntent

    // Remove game from collection
    data class RemoveGameFromCollection(val gameId: String) : CollectionsIntent

    data object DismissError : CollectionsIntent
    data object DismissSuccess : CollectionsIntent
    data object DismissCollectionDeleted : CollectionsIntent
}

class CollectionsViewModel(
    private val getMyCollectionsUseCase: GetMyCollectionsUseCase,
    private val getPublicCollectionsUseCase: GetPublicCollectionsUseCase,
    private val getCollectionDetailUseCase: GetCollectionDetailUseCase,
    private val createCollectionUseCase: CreateCollectionUseCase,
    private val updateCollectionUseCase: UpdateCollectionUseCase,
    private val deleteCollectionUseCase: DeleteCollectionUseCase,
    private val removeGameFromCollectionUseCase: RemoveGameFromCollectionUseCase,
    private val authRepository: AuthRepository,
    private val scrapeService: ScrapeService,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CollectionsState())
    val state: StateFlow<CollectionsState> = _state.asStateFlow()

    private var userIdLoaded = false

    fun onIntent(intent: CollectionsIntent) {
        ensureUserIdLoaded()
        when (intent) {
            CollectionsIntent.LoadMyCollections -> loadMyCollections()
            CollectionsIntent.LoadPublicCollections -> loadPublicCollections()
            is CollectionsIntent.LoadCollectionDetail -> loadDetail(intent.collectionId)

            // Create dialog
            CollectionsIntent.ShowCreateDialog -> _state.update { it.copy(showCreateDialog = true) }
            CollectionsIntent.DismissCreateDialog -> _state.update { it.copy(showCreateDialog = false) }
            is CollectionsIntent.CreateCollection -> createCollection(intent.name, intent.description, intent.isPublic)

            // Edit dialog
            CollectionsIntent.ShowEditDialog -> _state.update { it.copy(showEditDialog = true) }
            CollectionsIntent.DismissEditDialog -> _state.update { it.copy(showEditDialog = false) }
            is CollectionsIntent.UpdateCollection -> updateCollection(intent.name, intent.description, intent.isPublic)

            // Delete dialog
            CollectionsIntent.ShowDeleteDialog -> _state.update { it.copy(showDeleteDialog = true) }
            CollectionsIntent.DismissDeleteDialog -> _state.update { it.copy(showDeleteDialog = false) }
            CollectionsIntent.DeleteCollection -> deleteCollection()

            // Remove game
            is CollectionsIntent.RemoveGameFromCollection -> removeGameFromCollection(intent.gameId)

            CollectionsIntent.DismissError -> _state.update { it.copy(error = null) }
            CollectionsIntent.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            CollectionsIntent.DismissCollectionDeleted -> _state.update { it.copy(collectionDeleted = false) }
        }
    }

    private fun ensureUserIdLoaded() {
        if (userIdLoaded) return
        userIdLoaded = true
        scope.launch(dispatchers.io) {
            authRepository.getCurrentUser().onSuccess { user ->
                _state.update { it.copy(currentUserId = user.id) }
            }
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

    private fun createCollection(name: String, description: String?, isPublic: Boolean) {
        _state.update { it.copy(isCreating = true) }
        scope.launch(dispatchers.io) {
            createCollectionUseCase(
                name = name.trim(),
                description = description?.trim()?.ifBlank { null },
                isPublic = isPublic,
            ).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isCreating = false,
                            showCreateDialog = false,
                            successMessage = "Collection created",
                        )
                    }
                    loadMyCollections()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isCreating = false,
                            error = error.message ?: "Failed to create collection",
                        )
                    }
                },
            )
        }
    }

    private fun updateCollection(name: String?, description: String?, isPublic: Boolean?) {
        val detail = _state.value.selectedDetail ?: return
        _state.update { it.copy(isUpdating = true) }
        scope.launch(dispatchers.io) {
            updateCollectionUseCase(
                id = detail.id,
                name = name?.trim(),
                description = description?.trim(),
                isPublic = isPublic,
            ).fold(
                onSuccess = { updated ->
                    _state.update {
                        it.copy(
                            isUpdating = false,
                            showEditDialog = false,
                            selectedDetail = it.selectedDetail?.copy(
                                name = updated.name,
                                description = updated.description,
                                isPublic = updated.isPublic,
                            ),
                            successMessage = "Collection updated",
                        )
                    }
                    loadMyCollections()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isUpdating = false,
                            error = error.message ?: "Failed to update collection",
                        )
                    }
                },
            )
        }
    }

    private fun deleteCollection() {
        val detail = _state.value.selectedDetail ?: return
        _state.update { it.copy(isDeleting = true) }
        scope.launch(dispatchers.io) {
            deleteCollectionUseCase(detail.id).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            showDeleteDialog = false,
                            selectedDetail = null,
                            collectionDeleted = true,
                            successMessage = "Collection deleted",
                        )
                    }
                    loadMyCollections()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isDeleting = false,
                            showDeleteDialog = false,
                            error = error.message ?: "Failed to delete collection",
                        )
                    }
                },
            )
        }
    }

    private fun removeGameFromCollection(gameId: String) {
        val detail = _state.value.selectedDetail ?: return
        val originalIndex = detail.games.indexOfFirst { it.id == gameId }
        if (originalIndex == -1) return
        val removedGame = detail.games[originalIndex]

        // Optimistic update: remove from list immediately
        _state.update {
            it.copy(
                selectedDetail = it.selectedDetail?.copy(
                    games = it.selectedDetail.games.filter { g -> g.id != gameId },
                    gameCount = (it.selectedDetail.gameCount - 1).coerceAtLeast(0),
                ),
            )
        }

        scope.launch(dispatchers.io) {
            removeGameFromCollectionUseCase(detail.id, gameId).fold(
                onSuccess = {
                    _state.update { it.copy(successMessage = "Removed from collection") }
                },
                onFailure = { error ->
                    // Revert optimistic update: restore game at its original position
                    _state.update {
                        val currentGames = it.selectedDetail?.games ?: emptyList()
                        val insertIndex = originalIndex.coerceAtMost(currentGames.size)
                        val restoredGames = currentGames.toMutableList().apply {
                            add(insertIndex, removedGame)
                        }
                        it.copy(
                            selectedDetail = it.selectedDetail?.copy(
                                games = restoredGames,
                                gameCount = it.selectedDetail.gameCount + 1,
                            ),
                            error = error.message ?: "Failed to remove game",
                        )
                    }
                },
            )
        }
    }

    fun requestScrapeIfNeeded(game: Game) {
        if (game.coverUrl == null && game.scrapeAttempts == 0) {
            scrapeService.enqueueScrape(game.id)
        }
    }
}
