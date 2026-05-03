package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.DEFAULT_CONSOLE_ID
import com.spela.player.domain.model.DefaultKeyMappings
import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.model.detectDevicePreset
import com.spela.player.domain.model.getPlatformPresets
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.presentation.viewmodel.LibretroButtons
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

    override suspend fun clearBinding(consoleId: String, port: Int, retroButtonId: Int) {
        queries.deleteKeyMappingForButton(consoleId, port.toLong(), retroButtonId.toLong())
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

        // Fall back to hardcoded platform defaults, with per-console adjustments
        return getDefaultsForConsole(consoleId)
    }

    /**
     * Returns the platform default mapping, adjusted for the console type.
     *
     * For 2-button systems (NES, Game Boy, Game Gear, Master System), the face
     * buttons are shifted so the bottom-left pair (Y + B on Nintendo controllers,
     * X + A on Xbox controllers) maps to the system's two action buttons. This
     * feels more natural than using the bottom-right pair.
     */
    private fun getDefaultsForConsole(consoleId: String): Map<Int, Int> {
        // Filter the platform mapping down to the console's layout — any
        // libretro button the layout doesn't expose stays unbound. This
        // matters for consoles whose libretro core wires unsafe system
        // switches (reset, difficulty, ...) onto unused JOYPAD slots:
        // without filtering, the user's physical Y / X / L / R / etc.
        // would silently fire those switches mid-game. See #936 for the
        // Atari 7800 (prosystem) case.
        val layout = DefaultKeyMappings.allLayouts[consoleId.lowercase()]
        val mapping = if (layout != null) {
            val allowed = layout.buttons.map { it.retroButtonId }.toSet()
            platformDefaultMapping.filterKeys { it in allowed }
        } else {
            platformDefaultMapping
        }

        if (consoleId.lowercase() !in twoButtonConsoles) return mapping

        // Rotate face buttons so the left+bottom pair maps to B+A:
        //   B → left button (was Y's key)
        //   A → bottom button (was B's key)
        //   Y → top button (was X's key, unused by core)
        //   X → right button (was A's key, unused by core)
        val bKey = platformDefaultMapping[LibretroButtons.B]    // bottom
        val yKey = platformDefaultMapping[LibretroButtons.Y]    // left
        val aKey = platformDefaultMapping[LibretroButtons.A]    // right
        val xKey = platformDefaultMapping[LibretroButtons.X]    // top
        if (bKey == null || yKey == null || aKey == null || xKey == null) {
            return platformDefaultMapping
        }

        return buildMap {
            putAll(platformDefaultMapping)
            put(LibretroButtons.B, yKey)   // B → left
            put(LibretroButtons.A, bKey)   // A → bottom
            put(LibretroButtons.Y, xKey)   // Y → top (unused)
            put(LibretroButtons.X, aKey)   // X → right (unused)
        }
    }

    companion object {
        /** Console IDs for systems with only 2 action buttons (B + A). */
        private val twoButtonConsoles = setOf("nes", "gb", "gbc", "gg", "sms")
    }

    override fun getDefaultMapping(): Map<Int, Int> = platformDefaultMapping

    override fun getAvailablePresets(): List<KeyMappingPreset> = getPlatformPresets()

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun applyPreset(presetId: String) {
        val preset = getPlatformPresets().find { it.id == presetId } ?: return
        // Clear existing global default mappings
        queries.deleteKeyMappingsForConsole(DEFAULT_CONSOLE_ID, 0)
        // Write all preset bindings
        for ((retroButtonId, platformKeyCode) in preset.bindings) {
            queries.insertKeyMapping(
                id = Uuid.random().toString(),
                console_id = DEFAULT_CONSOLE_ID,
                port = 0,
                platform_key_code = platformKeyCode.toLong(),
                libretro_button_id = retroButtonId.toLong(),
            )
        }
    }

    override suspend fun ensureDefaultsApplied() {
        val localMappings = queries.getAllKeyMappings().executeAsList()
        if (localMappings.isNotEmpty()) return

        val presetId = detectDevicePreset() ?: return
        applyPreset(presetId)
    }

    override suspend fun getEffectiveMappingForGame(gameId: String, consoleId: String, port: Int): Map<Int, Int> {
        // 1. Per-game override
        val gameEntities = queries.getGameKeyMappings(gameId).executeAsList()
        if (gameEntities.isNotEmpty()) {
            return gameEntities.associate { it.libretro_button_id.toInt() to it.platform_key_code.toInt() }
        }

        // 2. Fall back to console/global/hardcoded chain
        return getEffectiveMapping(consoleId, port)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun setGameMapping(gameId: String, bindings: Map<Int, Int>) {
        queries.deleteGameKeyMappings(gameId)
        for ((retroButtonId, platformKeyCode) in bindings) {
            queries.insertGameKeyMapping(
                id = Uuid.random().toString(),
                game_id = gameId,
                libretro_button_id = retroButtonId.toLong(),
                platform_key_code = platformKeyCode.toLong(),
            )
        }
    }

    override suspend fun clearGameMapping(gameId: String) {
        queries.deleteGameKeyMappings(gameId)
    }

    override suspend fun hasGameMapping(gameId: String): Boolean {
        return queries.hasGameKeyMappings(gameId).executeAsOne() > 0
    }
}
