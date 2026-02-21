package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.SaveData
import com.spela.player.domain.repository.SaveDataRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SaveDataState(
    val gameId: String = "",
    val saveDataList: List<SaveData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showRenameDialog: Boolean = false,
    val renameTarget: SaveData? = null,
    val renameText: String = "",
    val showDeleteConfirm: Boolean = false,
    val deleteTarget: SaveData? = null,
    val successMessage: String? = null,
)

sealed interface SaveDataIntent {
    data class Load(val gameId: String) : SaveDataIntent
    data class Activate(val saveData: SaveData) : SaveDataIntent
    data class ShowRename(val saveData: SaveData) : SaveDataIntent
    data class UpdateRenameText(val text: String) : SaveDataIntent
    data object ConfirmRename : SaveDataIntent
    data object DismissRename : SaveDataIntent
    data class ShowDeleteConfirm(val saveData: SaveData) : SaveDataIntent
    data object ConfirmDelete : SaveDataIntent
    data object DismissDelete : SaveDataIntent
    data object DismissMessage : SaveDataIntent
}

class SaveDataViewModel(
    private val saveDataRepository: SaveDataRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SaveDataState())
    val state: StateFlow<SaveDataState> = _state.asStateFlow()

    fun onIntent(intent: SaveDataIntent) {
        when (intent) {
            is SaveDataIntent.Load -> load(intent.gameId)
            is SaveDataIntent.Activate -> activate(intent.saveData)
            is SaveDataIntent.ShowRename -> _state.update {
                it.copy(showRenameDialog = true, renameTarget = intent.saveData, renameText = intent.saveData.name)
            }
            is SaveDataIntent.UpdateRenameText -> _state.update { it.copy(renameText = intent.text) }
            SaveDataIntent.ConfirmRename -> confirmRename()
            SaveDataIntent.DismissRename -> _state.update { it.copy(showRenameDialog = false, renameTarget = null) }
            is SaveDataIntent.ShowDeleteConfirm -> _state.update {
                it.copy(showDeleteConfirm = true, deleteTarget = intent.saveData)
            }
            SaveDataIntent.ConfirmDelete -> confirmDelete()
            SaveDataIntent.DismissDelete -> _state.update { it.copy(showDeleteConfirm = false, deleteTarget = null) }
            SaveDataIntent.DismissMessage -> _state.update { it.copy(successMessage = null, error = null) }
        }
    }

    private fun load(gameId: String) {
        _state.update { it.copy(gameId = gameId, isLoading = true, error = null) }
        scope.launch(dispatchers.io) {
            saveDataRepository.getSaveDataList(gameId).fold(
                onSuccess = { list ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(saveDataList = list, isLoading = false) }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = error.message, isLoading = false) }
                    }
                },
            )
        }
    }

    private fun activate(saveData: SaveData) {
        scope.launch(dispatchers.io) {
            saveDataRepository.activateSaveData(_state.value.gameId, saveData.id.toString()).fold(
                onSuccess = {
                    withContext(dispatchers.main) {
                        _state.update { it.copy(successMessage = "Activated: ${saveData.name}") }
                    }
                    load(_state.value.gameId)
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = error.message) }
                    }
                },
            )
        }
    }

    private fun confirmRename() {
        val target = _state.value.renameTarget ?: return
        val newName = _state.value.renameText.trim()
        if (newName.isBlank()) return

        _state.update { it.copy(showRenameDialog = false) }
        scope.launch(dispatchers.io) {
            saveDataRepository.renameSaveData(_state.value.gameId, target.id.toString(), newName).fold(
                onSuccess = { load(_state.value.gameId) },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = error.message) }
                    }
                },
            )
        }
    }

    private fun confirmDelete() {
        val target = _state.value.deleteTarget ?: return
        _state.update { it.copy(showDeleteConfirm = false, deleteTarget = null) }
        scope.launch(dispatchers.io) {
            saveDataRepository.deleteSaveData(_state.value.gameId, target.id.toString()).fold(
                onSuccess = { load(_state.value.gameId) },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = error.message) }
                    }
                },
            )
        }
    }
}
