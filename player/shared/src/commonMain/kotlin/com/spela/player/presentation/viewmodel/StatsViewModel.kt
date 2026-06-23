package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.MeshStatMetric
import com.spela.player.domain.usecase.GetMeshAchieversUseCase
import com.spela.player.domain.usecase.GetMeshStatsUseCase
import com.spela.player.domain.usecase.GetMostActivePlayersUseCase
import com.spela.player.domain.usecase.GetMostPlayedGamesUseCase
import com.spela.player.domain.usecase.GetUserStatsUseCase
import com.spela.player.presentation.intent.StatsIntent
import com.spela.player.presentation.state.StatScope
import com.spela.player.presentation.state.StatsState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StatsViewModel(
    private val getMostPlayedGamesUseCase: GetMostPlayedGamesUseCase,
    private val getMostActivePlayersUseCase: GetMostActivePlayersUseCase,
    private val getUserStatsUseCase: GetUserStatsUseCase,
    private val getMeshStatsUseCase: GetMeshStatsUseCase,
    private val getMeshAchieversUseCase: GetMeshAchieversUseCase,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    fun onIntent(intent: StatsIntent) {
        when (intent) {
            StatsIntent.LoadStats -> loadStats()
            StatsIntent.DismissError -> _state.update { it.copy(error = null) }
            is StatsIntent.SetMostPlayedScope -> setMostPlayedScope(intent.scope)
            is StatsIntent.SetActivePlayersScope -> setActivePlayersScope(intent.scope)
            is StatsIntent.SetAchieversScope -> _state.update { it.copy(achieversScope = intent.scope) }
        }
    }

    private fun loadStats() {
        loadAchievers()
        _state.update { it.copy(isLoading = true, isLoadingPersonalStats = true) }
        scope.launch(dispatchers.io) {
            val games = getMostPlayedGamesUseCase().getOrDefault(emptyList())
            val players = getMostActivePlayersUseCase().getOrDefault(emptyList())
            val personal = getUserStatsUseCase().getOrNull()
            _state.update {
                it.copy(
                    mostPlayedGames = games,
                    activePlayers = players,
                    personalStats = personal,
                    isLoading = false,
                    isLoadingPersonalStats = false,
                    error = if (games.isEmpty() && players.isEmpty() && personal == null) "Failed to load stats" else null,
                )
            }
        }
    }

    // The "top achievers" mesh aggregate (local + connected servers) is loaded
    // eagerly with the rest of the stats; the section's scope toggle filters it
    // client-side. Best-effort — a failure just leaves the section empty.
    private fun loadAchievers() {
        _state.update { it.copy(isLoadingMeshAchievers = true) }
        scope.launch(dispatchers.io) {
            val achievers = getMeshAchieversUseCase().getOrDefault(emptyList())
            _state.update { it.copy(meshAchievers = achievers, isLoadingMeshAchievers = false) }
        }
    }

    // Mesh stats are loaded lazily — only when the viewer switches a section to
    // the "across connected servers" scope (refreshed on each switch).
    private fun setMostPlayedScope(newScope: StatScope) {
        _state.update { it.copy(mostPlayedScope = newScope) }
        if (newScope == StatScope.AcrossServers) {
            _state.update { it.copy(isLoadingMeshMostPlayed = true) }
            scope.launch(dispatchers.io) {
                val stats = getMeshStatsUseCase(MeshStatMetric.GamePlay).getOrDefault(emptyList())
                _state.update { it.copy(meshMostPlayed = stats, isLoadingMeshMostPlayed = false) }
            }
        }
    }

    private fun setActivePlayersScope(newScope: StatScope) {
        _state.update { it.copy(activePlayersScope = newScope) }
        if (newScope == StatScope.AcrossServers) {
            _state.update { it.copy(isLoadingMeshActivePlayers = true) }
            scope.launch(dispatchers.io) {
                val stats = getMeshStatsUseCase(MeshStatMetric.PlayerPlay).getOrDefault(emptyList())
                _state.update { it.copy(meshActivePlayers = stats, isLoadingMeshActivePlayers = false) }
            }
        }
    }
}
