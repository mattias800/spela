package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.presentation.intent.ChallengeIntent
import com.spela.player.presentation.state.ChallengeListState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChallengeListViewModel(
    private val challengeRepository: ChallengeRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ChallengeListState())
    val state: StateFlow<ChallengeListState> = _state.asStateFlow()

    fun onIntent(intent: ChallengeIntent) {
        when (intent) {
            is ChallengeIntent.LoadChallenges -> loadChallenges(intent.gameId, intent.sort)
            is ChallengeIntent.LoadGameChallenges -> loadGameChallenges(intent.gameId)
            ChallengeIntent.LoadMyChallenges -> loadMyChallenges()
            ChallengeIntent.DismissError -> _state.update { it.copy(error = null) }
            else -> { /* Handled by ChallengeDetailViewModel */ }
        }
    }

    private fun loadChallenges(gameId: String?, sort: String?) {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            challengeRepository.getChallenges(gameId = gameId, sort = sort).fold(
                onSuccess = { challenges ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(challenges = challenges, isLoading = false) }
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

    private fun loadGameChallenges(gameId: String) {
        _state.update { it.copy(isLoadingGameChallenges = true) }
        scope.launch(dispatchers.io) {
            challengeRepository.getGameChallenges(gameId).fold(
                onSuccess = { challenges ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(gameChallenges = challenges, isLoadingGameChallenges = false) }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = error.message, isLoadingGameChallenges = false) }
                    }
                },
            )
        }
    }

    private fun loadMyChallenges() {
        _state.update { it.copy(isLoadingMyChallenges = true) }
        scope.launch(dispatchers.io) {
            challengeRepository.getMyChallenges().fold(
                onSuccess = { challenges ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(myChallenges = challenges, isLoadingMyChallenges = false) }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = error.message, isLoadingMyChallenges = false) }
                    }
                },
            )
        }
    }
}
