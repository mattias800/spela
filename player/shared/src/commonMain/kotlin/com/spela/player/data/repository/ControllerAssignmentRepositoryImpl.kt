package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.repository.ControllerAssignmentRepository

/**
 * SQLDelight-backed [ControllerAssignmentRepository]. Reads/writes are synchronous
 * (see the interface doc): the manager consults this on the input thread when a
 * controller connects. `player_slot` is a nullable column — a stored NULL is a
 * remembered "cleared" state, distinct from an absent row (never seen).
 */
class ControllerAssignmentRepositoryImpl(
    database: SpelaDatabase,
) : ControllerAssignmentRepository {

    private val queries = database.spelaDatabaseQueries

    override fun getAll(): Map<String, Int?> =
        queries.getAllControllerAssignments().executeAsList()
            .associate { it.stable_key to it.player_slot?.toInt() }

    override fun put(stableKey: String, slot: Int?) {
        queries.upsertControllerAssignment(stableKey, slot?.toLong())
    }
}
