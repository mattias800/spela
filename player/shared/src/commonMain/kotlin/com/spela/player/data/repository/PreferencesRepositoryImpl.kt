package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.client.models.ConsoleKeyMappingDTO
import com.spela.client.models.UpdateDevicePreferencesRequest
import com.spela.client.models.UpdateGameKeyMappingRequest
import com.spela.client.models.UpdatePreferencesRequest
import com.spela.client.models.UserPreferencesResponse
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.DEFAULT_CONSOLE_ID
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.libretro.GamepadMappingMigration
import com.spela.player.util.currentPlatform
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.domain.repository.PreferencesRepository
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PreferencesRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val database: SpelaDatabase,
    private val deviceManager: DeviceManager,
    private val keyMappingRepository: KeyMappingRepository,
) : PreferencesRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private companion object {
        /** Device-local flag marking the one-time legacy gamepad keycode → positional migration done. */
        const val GAMEPAD_MIGRATION_FLAG = "gamepad_positional_migration_v1"
    }

    override suspend fun getPreferences(): Result<UserPreferences> {
        return runCatching {
            val dto = apiClient.getPreferences()
            cachePreferences(dto)
            dto.toDomain()
        }.recoverCatching {
            getCachedPreferences() ?: throw it
        }
    }

    private fun cachePreferences(dto: UserPreferencesResponse) {
        val jsonString = json.encodeToString(dto)
        database.spelaDatabaseQueries.upsertCachedPreferences(
            json_data = jsonString,
            updated_at = Clock.System.now().toEpochMilliseconds(),
        )
    }

    private fun getCachedPreferences(): UserPreferences? {
        val cached = database.spelaDatabaseQueries.getCachedPreferences().executeAsOneOrNull()
            ?: return null
        return runCatching {
            json.decodeFromString<UserPreferencesResponse>(cached.json_data).toDomain()
        }.getOrNull()
    }

    override suspend fun updatePreferences(
        showPerformanceOverlay: Boolean?,
        autoSaveEnabled: Boolean?,
        autoLoadSaveEnabled: Boolean?,
        autoUpdateCoresEnabled: Boolean?,
        selectedShader: String?,
        selectedTheme: String?,
        consoleShaders: Map<String, String>?,
        consoleSaveStatePolicies: Map<String, String>?,
        gameSaveStatePolicies: Map<String, String>?,
        defaultSecondScreenPage: String?,
    ): Result<UserPreferences> = runCatching {
        apiClient.updatePreferences(
            UpdatePreferencesRequest(
                showPerformanceOverlay = showPerformanceOverlay,
                autoSaveEnabled = autoSaveEnabled,
                autoLoadSaveEnabled = autoLoadSaveEnabled,
                autoUpdateCoresEnabled = autoUpdateCoresEnabled,
                selectedShader = selectedShader,
                selectedTheme = selectedTheme,
                consoleShaders = consoleShaders,
                consoleSaveStatePolicies = consoleSaveStatePolicies,
                gameSaveStatePolicies = gameSaveStatePolicies,
                defaultSecondScreenPage = defaultSecondScreenPage,
            )
        ).toDomain()
    }

    override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? {
        return database.spelaDatabaseQueries.getShaderOverride(consoleId)
            .executeAsOneOrNull()
            ?.let { ShaderPreset.fromApiId(it) }
    }

    override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {
        if (shader == null || shader == ShaderPreset.NONE) {
            database.spelaDatabaseQueries.deleteShaderOverride(consoleId)
        } else {
            database.spelaDatabaseQueries.insertShaderOverride(consoleId, shader.apiId)
        }
    }

    override fun getAllDeviceShaderOverrides(): Map<String, ShaderPreset> {
        return database.spelaDatabaseQueries.getAllShaderOverrides()
            .executeAsList()
            .associate { it.console_id to ShaderPreset.fromApiId(it.shader) }
    }

    override suspend fun syncDeviceShaderOverrides() {
        val serverDeviceId = deviceManager.getServerDeviceId() ?: return
        runCatching {
            val serverDevice = apiClient.getDevices().find {
                it.id == serverDeviceId
            } ?: return

            // Merge server device shader overrides into local cache
            for ((consoleId, shader) in serverDevice.consoleShaders) {
                val preset = ShaderPreset.fromApiId(shader)
                if (preset != ShaderPreset.NONE) {
                    database.spelaDatabaseQueries.insertShaderOverride(consoleId, preset.apiId)
                }
            }
        }
    }

    override suspend fun pushDeviceShaderOverridesToServer() {
        val serverDeviceId = deviceManager.getServerDeviceId() ?: return
        val overrides = getAllDeviceShaderOverrides()
        val shaderMap = overrides.mapValues { it.value.apiId }
        runCatching {
            apiClient.updateDevicePreferences(serverDeviceId, UpdateDevicePreferencesRequest(shaderMap))
        }
    }

    override suspend fun resolveShader(consoleId: String): ShaderPreset {
        // 1. Check device-local override first
        val deviceOverride = getDeviceShaderOverride(consoleId)
        if (deviceOverride != null) return deviceOverride

        // 2. Try preferences from API (with cache fallback)
        val preferences = getPreferences().getOrNull()
        if (preferences != null) {
            val consoleShader = preferences.consoleShaders[consoleId]
            if (consoleShader != null) return consoleShader

            // 3. Fall back to global selected shader
            return preferences.selectedShader
        }

        // 4. Final fallback
        return ShaderPreset.NONE
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun syncKeyMappingsFromServer() {
        runCatching {
            val prefs = apiClient.getPreferences()

            // Import positional gamepad mappings (#1334) FIRST and independently:
            // they are platform-independent (GamepadPosition -> RetroPad), so no
            // key-code validation applies, and the keycode early-return below must
            // not skip them. Only import when this device has no positional
            // overrides yet, so a local edit is never clobbered.
            if (database.spelaDatabaseQueries.getAllGamepadMappings().executeAsList().isEmpty()) {
                for ((consoleId, mapping) in prefs.consoleKeyMappings) {
                    for ((positionName, retroId) in mapping.positionMappings) {
                        database.spelaDatabaseQueries.insertGamepadMapping(
                            id = "$consoleId:0:$positionName",
                            console_id = consoleId,
                            port = 0,
                            gamepad_position = positionName,
                            libretro_button_id = retroId,
                        )
                    }
                }
            }

            // Import keycode mappings only on the first-device experience (empty
            // local DB). Key codes are platform-specific (Android KEYCODE_*,
            // desktop AWT VK_*), so validate the server's codes match this
            // platform before importing. NOTE: this is a nested guard, not an
            // early return, so the legacy->positional migration below still runs
            // on existing installs.
            val localMappings = database.spelaDatabaseQueries.getAllKeyMappings().executeAsList()
            if (localMappings.isEmpty()) {
                val platformDefaults = keyMappingRepository.getDefaultMapping()
                val serverCodes = prefs.customKeyMapping.values.mapNotNull { it.toIntOrNull() }.toSet()
                val platformCodes = platformDefaults.values.toSet()
                val platformMatches = serverCodes.isEmpty() || serverCodes.intersect(platformCodes).isNotEmpty()
                if (platformMatches) {
                    // Import global default mapping from server
                    for ((retroButtonStr, keyCodeStr) in prefs.customKeyMapping) {
                        val retroButton = retroButtonStr.toIntOrNull() ?: continue
                        val keyCode = keyCodeStr.toIntOrNull() ?: continue
                        database.spelaDatabaseQueries.insertKeyMapping(
                            id = Uuid.random().toString(),
                            console_id = DEFAULT_CONSOLE_ID,
                            port = 0,
                            platform_key_code = keyCode.toLong(),
                            libretro_button_id = retroButton.toLong(),
                        )
                    }
                    // Import per-console mappings from server
                    for ((consoleId, mapping) in prefs.consoleKeyMappings) {
                        for ((retroButtonStr, keyCodeStr) in mapping.customMapping.orEmpty()) {
                            val retroButton = retroButtonStr.toIntOrNull() ?: continue
                            val keyCode = keyCodeStr.toIntOrNull() ?: continue
                            database.spelaDatabaseQueries.insertKeyMapping(
                                id = Uuid.random().toString(),
                                console_id = consoleId,
                                port = 0,
                                platform_key_code = keyCode.toLong(),
                                libretro_button_id = retroButton.toLong(),
                            )
                        }
                    }
                }
            }

            // Migrate any legacy per-console Android gamepad keycode mappings into
            // the positional layer (#1334). One-time, Android-only, additive.
            migrateLegacyGamepadKeycodeMappings()
        }
    }

    /**
     * One-time, Android-only migration (#1334): convert legacy per-console
     * gamepad key-code mappings into the positional layer, preserving genuine
     * customizations. Additive (never deletes key-code rows nor clobbers an
     * existing positional override) and guarded by a device-local flag so it
     * runs at most once. Desktop is skipped — its KeyMappingEntity holds keyboard
     * codes that must not be normalized as gamepad codes.
     */
    private fun migrateLegacyGamepadKeycodeMappings() {
        if (currentPlatform() != "android") return
        val queries = database.spelaDatabaseQueries
        if (queries.getDeviceSetting(GAMEPAD_MIGRATION_FLAG).executeAsOneOrNull() != null) return

        val legacy = queries.getAllKeyMappings().executeAsList().map {
            GamepadMappingMigration.LegacyRow(
                consoleId = it.console_id,
                port = it.port.toInt(),
                keyCode = it.platform_key_code.toInt(),
                retroButtonId = it.libretro_button_id.toInt(),
            )
        }
        val existing = queries.getAllGamepadMappings().executeAsList()
            .map { Triple(it.console_id, it.port, it.gamepad_position) }.toSet()
        for (entry in GamepadMappingMigration.split(legacy)) {
            if (Triple(entry.consoleId, entry.port.toLong(), entry.position.name) in existing) continue
            queries.insertGamepadMapping(
                id = "${entry.consoleId}:${entry.port}:${entry.position.name}",
                console_id = entry.consoleId,
                port = entry.port.toLong(),
                gamepad_position = entry.position.name,
                libretro_button_id = entry.retroButtonId.toLong(),
            )
        }
        queries.insertDeviceSetting(GAMEPAD_MIGRATION_FLAG, "done")
    }

    override fun getOrientationLock(): String {
        return database.spelaDatabaseQueries.getDeviceSetting("orientation_lock")
            .executeAsOneOrNull() ?: "auto"
    }

    override fun setOrientationLock(mode: String) {
        database.spelaDatabaseQueries.insertDeviceSetting("orientation_lock", mode)
    }

    override fun getControlTab(consoleId: String): String {
        return database.spelaDatabaseQueries.getDeviceSetting("control_tab:$consoleId")
            .executeAsOneOrNull()
            ?: if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
    }

    override fun setControlTab(consoleId: String, tab: String) {
        database.spelaDatabaseQueries.insertDeviceSetting("control_tab:$consoleId", tab)
    }

    override fun getConsoleListGrouping(): String {
        return database.spelaDatabaseQueries.getDeviceSetting("console_list_grouping")
            .executeAsOneOrNull() ?: "generation"
    }

    override fun setConsoleListGrouping(grouping: String) {
        database.spelaDatabaseQueries.insertDeviceSetting("console_list_grouping", grouping)
    }

    override suspend fun pushKeyMappingsToServer() {
        runCatching {
            val allMappings = database.spelaDatabaseQueries.getAllKeyMappings().executeAsList()

            // Build global default mapping (consoleId == __default__)
            val globalMapping = allMappings
                .filter { it.console_id == DEFAULT_CONSOLE_ID }
                .associate { it.libretro_button_id.toString() to it.platform_key_code.toString() }

            // Per-console keycode mappings.
            val keycodeByConsole = allMappings
                .filter { it.console_id != DEFAULT_CONSOLE_ID }
                .groupBy { it.console_id }
                .mapValues { (_, entities) ->
                    entities.associate {
                        it.libretro_button_id.toString() to it.platform_key_code.toString()
                    }
                }

            // Per-console positional gamepad mappings (#1334). Only the stored
            // diffs from default are synced (port 0 = the synced primary);
            // platform-independent, so no validation. Value is the RetroPad id.
            val positionByConsole = database.spelaDatabaseQueries.getAllGamepadMappings().executeAsList()
                .filter { it.port == 0L }
                .groupBy { it.console_id }
                .mapValues { (_, rows) -> rows.associate { it.gamepad_position to it.libretro_button_id } }

            // A console is pushed if it has EITHER layer; the shared DTO requires
            // both fields, so missing layers are sent empty (never null) and so a
            // positional edit never wipes the keycode layer and vice-versa.
            val consoleMappings = (keycodeByConsole.keys + positionByConsole.keys).associateWith { consoleId ->
                ConsoleKeyMappingDTO(
                    selectedMapping = "",
                    customMapping = keycodeByConsole[consoleId] ?: emptyMap(),
                    positionMappings = positionByConsole[consoleId] ?: emptyMap(),
                )
            }

            apiClient.updatePreferences(
                UpdatePreferencesRequest(
                    customKeyMapping = globalMapping,
                    consoleKeyMappings = consoleMappings,
                )
            )
        }
    }

    override suspend fun pushGameKeyMappingToServer(gameId: String, bindings: Map<Int, Int>) {
        runCatching {
            apiClient.updateGameKeyMapping(
                gameId,
                UpdateGameKeyMappingRequest(
                    customMapping = bindings.entries.associate { it.key.toString() to it.value.toString() },
                ),
            )
        }
    }

    override suspend fun deleteGameKeyMappingOnServer(gameId: String) {
        runCatching { apiClient.deleteGameKeyMapping(gameId) }
    }

    override suspend fun syncGameKeyMappingFromServer(gameId: String) {
        runCatching {
            val response = apiClient.getGameKeyMapping(gameId)
            val bindings = response.customMapping.entries.mapNotNull { (retro, key) ->
                val r = retro.toIntOrNull() ?: return@mapNotNull null
                val k = key.toIntOrNull() ?: return@mapNotNull null
                r to k
            }.toMap()
            // Only import a real server override; never wipe a local-only override
            // when the server has none (e.g. an offline save not yet pushed).
            if (bindings.isNotEmpty()) {
                keyMappingRepository.setGameMapping(gameId, bindings)
            }
        }
    }
}
