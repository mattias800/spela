package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.presentation.intent.ChallengeIntent
import com.spela.player.presentation.state.ChallengeDetailState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChallengeDetailViewModel(
    private val challengeRepository: ChallengeRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ChallengeDetailState())
    val state: StateFlow<ChallengeDetailState> = _state.asStateFlow()

    fun onIntent(intent: ChallengeIntent) {
        when (intent) {
            is ChallengeIntent.LoadChallengeDetail -> loadDetail(intent.challengeId)
            is ChallengeIntent.LoadLeaderboard -> loadLeaderboard(intent.challengeId, page = 1)
            is ChallengeIntent.LoadMoreLeaderboard -> {
                val nextPage = _state.value.leaderboardPage + 1
                loadLeaderboard(intent.challengeId, page = nextPage)
            }
            is ChallengeIntent.DeleteChallenge -> deleteChallenge(intent.challengeId)
            ChallengeIntent.DismissError -> _state.update { it.copy(error = null) }
            else -> { /* Handled by ChallengeListViewModel */ }
        }
    }

    private fun loadDetail(challengeId: String) {
        _state.update { it.copy(isLoading = true, deleteSuccess = false) }
        scope.launch(dispatchers.io) {
            challengeRepository.getChallengeDetail(challengeId).fold(
                onSuccess = { challenge ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(challenge = challenge, isLoading = false) }
                    }
                    // Also load leaderboard and my attempts in parallel
                    loadLeaderboard(challengeId, page = 1)
                    loadMyAttempts(challengeId)
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = error.message, isLoading = false) }
                    }
                },
            )
        }
    }

    private fun loadLeaderboard(challengeId: String, page: Int) {
        _state.update { it.copy(isLoadingLeaderboard = true) }
        scope.launch(dispatchers.io) {
            challengeRepository.getLeaderboard(challengeId, page).fold(
                onSuccess = { entries ->
                    withContext(dispatchers.main) {
                        _state.update {
                            val combined = if (page == 1) entries else it.leaderboard + entries
                            it.copy(
                                leaderboard = combined,
                                isLoadingLeaderboard = false,
                                leaderboardPage = page,
                                hasMoreLeaderboard = entries.size >= 50,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = error.message, isLoadingLeaderboard = false) }
                    }
                },
            )
        }
    }

    private fun loadMyAttempts(challengeId: String) {
        scope.launch(dispatchers.io) {
            challengeRepository.getMyAttempts(challengeId).onSuccess { attempts ->
                withContext(dispatchers.main) {
                    _state.update { it.copy(myAttempts = attempts) }
                }
            }
        }
    }

    private fun deleteChallenge(challengeId: String) {
        scope.launch(dispatchers.io) {
            challengeRepository.deleteChallenge(challengeId).fold(
                onSuccess = {
                    withContext(dispatchers.main) {
                        _state.update { it.copy(deleteSuccess = true) }
                    }
                },
                onFailure = { error ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(error = "Failed to delete: ${error.message}") }
                    }
                },
            )
        }
    }
}
