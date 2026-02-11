package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.DEFAULT_CONSOLE_ID
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.repository.KeyMappingRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * @param database SQLDelight database instance
 * @param platformDefaultMapping The hardcoded default mapping for the current platform
 *        (retroButtonId -> platformKeyCode). Provided by the platform Koin module.
 */
class KeyMappingRepositoryImpl(
    private val database: SpelaDatabase,
    private val platformDefaultMapping: Map<Int, Int>,
) : KeyMappingRepository {

    private val queries = database.spelaDatabaseQueries

    override suspend fun getMappingForConsole(consoleId: String, port: Int): KeyMappingProfile? {
        val entities = queries.getKeyMappingsForConsole(consoleId, port.toLong()).executeAsList()
        if (entities.isEmpty()) return null
        val bindings = entities.associate { it.libretro_button_id.toInt() to it.platform_key_code.toInt() }
        return KeyMappingProfile(
            consoleId = consoleId,
            port = port,
            bindings = bindings,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun setBinding(consoleId: String, port: Int, retroButtonId: Int, platformKeyCode: Int) {
        val id = Uuid.random().toString()
        queries.insertKeyMapping(
            id = id,
            console_id = consoleId,
            port = port.toLong(),
            platform_key_code = platformKeyCode.toLong(),
            libretro_button_id = retroButtonId.toLong(),
        )
    }

    override suspend fun resetToDefault(consoleId: String, port: Int) {
        queries.deleteKeyMappingsForConsole(consoleId, port.toLong())
    }

    override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<Int, Int> {
        // Try console-specific first
        val consoleMapping = getMappingForConsole(consoleId, port)
        if (consoleMapping != null) return consoleMapping.bindings

        // Try global default from DB
        if (consoleId != DEFAULT_CONSOLE_ID) {
            val globalMapping = getMappingForConsole(DEFAULT_CONSOLE_ID, port)
            if (globalMapping != null) return globalMapping.bindings
        }

        // Fall back to hardcoded platform defaults
        return platformDefaultMapping
    }

    override fun getDefaultMapping(): Map<Int, Int> = platformDefaultMapping
}
