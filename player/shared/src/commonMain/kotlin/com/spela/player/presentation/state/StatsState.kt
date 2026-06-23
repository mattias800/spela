package com.spela.player.presentation.state

import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MeshAchiever
import com.spela.player.domain.model.MeshStat
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.domain.model.UserStats

/** Whether a stats section shows this server's data or the connected-server mesh. */
enum class StatScope { ThisServer, AcrossServers }

data class StatsState(
    val mostPlayedGames: List<MostPlayedGame> = emptyList(),
    val activePlayers: List<ActivePlayer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val personalStats: UserStats? = null,
    val isLoadingPersonalStats: Boolean = false,
    // Cross-mesh scope per section + lazily-loaded federated leaderboards.
    val mostPlayedScope: StatScope = StatScope.ThisServer,
    val activePlayersScope: StatScope = StatScope.ThisServer,
    val meshMostPlayed: List<MeshStat> = emptyList(),
    val meshActivePlayers: List<MeshStat> = emptyList(),
    val isLoadingMeshMostPlayed: Boolean = false,
    val isLoadingMeshActivePlayers: Boolean = false,
    // Top achievers: one mesh aggregate (local + connected), scope toggle filters
    // it client-side by hop. Loaded eagerly with the rest of the stats.
    val achieversScope: StatScope = StatScope.AcrossServers,
    val meshAchievers: List<MeshAchiever> = emptyList(),
    val isLoadingMeshAchievers: Boolean = false,
)
