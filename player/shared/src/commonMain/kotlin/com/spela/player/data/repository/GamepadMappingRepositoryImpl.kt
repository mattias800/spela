package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.DefaultGamepadMapping
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.repository.GamepadMappingRepository

/**
 * SQLDelight-backed [GamepadMappingRepository]. Stores only per-console/port
 * overrides in `GamepadMappingEntity`; the effective mapping layers them on top
 * of [DefaultGamepadMapping].
 *
 * Idempotent: the row id is derived from `(consoleId, port, position)`, so
 * re-setting the same binding replaces the same row rather than accumulating —
 * `f(f(x)) = f(x)`. An override whose stored position no longer parses to a
 * [GamepadPosition] (e.g. a renamed enum constant from an old build) is ignored.
 */
class GamepadMappingRepositoryImpl(
    database: SpelaDatabase,
) : GamepadMappingRepository {

    private val queries = database.spelaDatabaseQueries

    override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<GamepadPosition, Int> {
        val overrides = queries.getGamepadMappingsForConsole(consoleId, port.toLong())
            .executeAsList()
            .mapNotNull { row ->
                val position = GamepadPosition.entries.firstOrNull { it.name == row.gamepad_position }
                    ?: return@mapNotNull null
                position to row.libretro_button_id.toInt()
            }
            .toMap()
        return DefaultGamepadMapping.POSITION_TO_RETRO + overrides
    }

    override suspend fun setBinding(consoleId: String, port: Int, position: GamepadPosition, retroButtonId: Int) {
        queries.insertGamepadMapping(
            id = rowId(consoleId, port, position),
            console_id = consoleId,
            port = port.toLong(),
            gamepad_position = position.name,
            libretro_button_id = retroButtonId.toLong(),
        )
    }

    override suspend fun resetToDefault(consoleId: String, port: Int) {
        queries.deleteGamepadMappingsForConsole(consoleId, port.toLong())
    }

    override fun getDefaultMapping(): Map<GamepadPosition, Int> = DefaultGamepadMapping.POSITION_TO_RETRO

    private fun rowId(consoleId: String, port: Int, position: GamepadPosition) =
        "$consoleId:$port:${position.name}"
}
