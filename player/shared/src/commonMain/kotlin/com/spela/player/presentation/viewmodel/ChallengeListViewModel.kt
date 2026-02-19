package com.spela.player.presentation.viewmodel

import com.spela.player.domain.repository.ChallengeRepository
import com.spela.player.domain.usecase.GetConsolesUseCase
import com.spela.player.presentation.intent.ChallengeIntent
import com.spela.player.presentation.state.ChallengeListState
import com.spela.player.presentation.state.ChallengeTab
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
    private val getConsolesUseCase: GetConsolesUseCase,
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
            is ChallengeIntent.SelectTab -> selectTab(intent.tab)
            is ChallengeIntent.FilterByConsole -> filterByConsole(intent.consoleId)
            is ChallengeIntent.FilterByDifficulty -> filterByDifficulty(intent.difficulty)
            ChallengeIntent.LoadConsoles -> loadConsoles()
            else -> { /* Handled by ChallengeDetailViewModel */ }
        }
    }

    private fun selectTab(tab: ChallengeTab) {
        _state.update { it.copy(selectedTab = tab) }
        when (tab) {
            ChallengeTab.POPULAR -> reloadBrowseChallenges()
            ChallengeTab.RECENT -> reloadBrowseChallenges()
            ChallengeTab.MINE -> loadMyChallenges()
        }
    }

    private fun filterByConsole(consoleId: String?) {
        _state.update { it.copy(selectedConsoleFilter = consoleId) }
        reloadBrowseChallenges()
    }

    private fun filterByDifficulty(difficulty: String?) {
        _state.update { it.copy(selectedDifficultyFilter = difficulty) }
        reloadBrowseChallenges()
    }

    private fun loadConsoles() {
        scope.launch(dispatchers.io) {
            getConsolesUseCase().fold(
                onSuccess = { consoles ->
                    withContext(dispatchers.main) {
                        _state.update { it.copy(consoles = consoles) }
                    }
                },
                onFailure = { /* non-critical, filter will just be empty */ },
            )
        }
    }

    private fun reloadBrowseChallenges() {
        val current = _state.value
        val sort = when (current.selectedTab) {
            ChallengeTab.POPULAR -> "most_attempted"
            ChallengeTab.RECENT -> "newest"
            ChallengeTab.MINE -> return
        }
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            challengeRepository.getChallenges(
                consoleId = current.selectedConsoleFilter,
                difficulty = current.selectedDifficultyFilter,
                sort = sort,
            ).fold(
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
