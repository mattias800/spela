package com.spela.player.domain.repository

import com.spela.player.domain.model.ConnectedConsole
import com.spela.player.domain.model.FriendPresence
import com.spela.player.domain.model.ImportJob
import com.spela.player.domain.model.MeshStat
import com.spela.player.domain.model.MeshStatMetric
import com.spela.player.domain.model.RemoteGame

/**
 * Discovery of games on connected federation servers, and importing them into
 * the local library. Backed by the server's federation catalog + import
 * endpoints. See #1391 / #1385.
 */
interface FederationRepository {
    /** Consoles that have connected-server games, with per-console counts (browse overview). */
    suspend fun getConnectedConsoles(): Result<List<ConnectedConsole>>

    /** Connected-server games for one console. */
    suspend fun getGamesForConsole(console: String): Result<List<RemoteGame>>

    /** A single connected-server game by its cross-key (deep link / refresh). Null when not offered. */
    suspend fun getRemoteGame(key: String): Result<RemoteGame?>

    /** Start importing a connected-server game into the local library. */
    suspend fun startImport(key: String, title: String, console: String): Result<ImportJob>

    /** All import jobs with their status + progress (newest first). */
    suspend fun listImports(): Result<List<ImportJob>>

    /** Players active across the mesh right now (local + connected servers). */
    suspend fun getAggregatedPresence(): Result<List<FriendPresence>>

    /** Federated (mesh) leaderboard for the given metric, summed across the mesh. */
    suspend fun getAggregatedStats(metric: MeshStatMetric): Result<List<MeshStat>>
}
