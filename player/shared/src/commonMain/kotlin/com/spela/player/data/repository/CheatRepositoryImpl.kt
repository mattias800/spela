package com.spela.player.data.repository

import com.spela.player.CheatEntity
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.model.Cheat
import com.spela.player.domain.repository.CheatRepository
import kotlin.time.Clock

class CheatRepositoryImpl(
    private val database: SpelaDatabase,
    private val apiClient: SpelaApiClient,
) : CheatRepository {

    private val queries = database.spelaDatabaseQueries

    override suspend fun getCheatsForGame(gameId: String): Result<List<Cheat>> = runCatching {
        try {
            val dtos = apiClient.getGameCheats(gameId)
            // Build set of currently-enabled cheat IDs to preserve enabled state
            val existingEnabled: Set<String> = queries
                .getEnabledGameCheats(gameId)
                .executeAsList()
                .map { it.id }
                .toSet()

            // Upsert from server, preserving enabled state
            dtos.forEach { dto ->
                val cheatId = "${gameId}_${dto.index}"
                val wasEnabled = cheatId in existingEnabled
                queries.upsertCheat(
                    id = cheatId,
                    game_id = gameId,
                    cheat_index = dto.index.toLong(),
                    description = dto.description,
                    code = dto.code,
                    enabled = if (wasEnabled) 1L else 0L,
                    cached_at = Clock.System.now().toEpochMilliseconds(),
                )
            }

            // Return full list from local DB
            queries.getGameCheats(gameId).executeAsList().map { it.toDomain() }
        } catch (_: Exception) {
            // Fallback to cached data on network error
            queries.getGameCheats(gameId).executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun setCheatEnabled(cheatId: String, enabled: Boolean) {
        queries.setCheatEnabled(
            enabled = if (enabled) 1L else 0L,
            id = cheatId,
        )
    }

    override suspend fun disableAllCheats(gameId: String) {
        queries.setAllCheatsEnabled(enabled = 0L, game_id = gameId)
    }

    override fun getEnabledCheats(gameId: String): List<Cheat> =
        queries.getEnabledGameCheats(gameId).executeAsList().map { it.toDomain() }
}

private fun CheatEntity.toDomain() = Cheat(
    id = id,
    gameId = game_id,
    index = cheat_index.toInt(),
    description = description,
    code = code,
    enabled = enabled == 1L,
)
