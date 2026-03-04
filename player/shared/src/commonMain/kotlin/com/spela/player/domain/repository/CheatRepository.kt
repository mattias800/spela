package com.spela.player.domain.repository

import com.spela.player.domain.model.Cheat

interface CheatRepository {
    /** Fetch cheats from server, upsert into local DB (preserving enabled state), return full list. */
    suspend fun getCheatsForGame(gameId: String): Result<List<Cheat>>
    /** Update enabled flag locally (optimistic, no server sync). */
    suspend fun setCheatEnabled(cheatId: String, enabled: Boolean)
    /** Disable all cheats for a game locally. */
    suspend fun disableAllCheats(gameId: String)
    /** Synchronous read from local DB — used at game launch. */
    fun getEnabledCheats(gameId: String): List<Cheat>
}
