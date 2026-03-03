package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.ConsoleKeyMappingDto
import com.spela.player.data.remote.dto.UpdateDevicePreferencesRequest
import com.spela.player.data.remote.dto.UpdatePreferencesRequest
import com.spela.player.data.remote.dto.UserPreferencesDto
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.DEFAULT_CONSOLE_ID
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.domain.repository.PreferencesRepository
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
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

    override suspend fun getPreferences(): Result<UserPreferences> {
        return runCatching {
            val dto = apiClient.getPreferences()
            cachePreferences(dto)
            dto.toDomain()
        }.recoverCatching {
            getCachedPreferences() ?: throw it
        }
    }

    private fun cachePreferences(dto: UserPreferencesDto) {
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
            json.decodeFromString<UserPreferencesDto>(cached.json_data).toDomain()
        }.getOrNull()
    }

    override suspend fun updatePreferences(
        showPerformanceOverlay: Boolean?,
        autoSaveEnabled: Boolean?,
        autoLoadSaveEnabled: Boolean?,
        selectedShader: String?,
        selectedTheme: String?,
        consoleShaders: Map<String, String>?,
    ): Result<UserPreferences> = runCatching {
        apiClient.updatePreferences(
            UpdatePreferencesRequest(
                showPerformanceOverlay = showPerformanceOverlay,
                autoSaveEnabled = autoSaveEnabled,
                autoLoadSaveEnabled = autoLoadSaveEnabled,
                selectedShader = selectedShader,
                selectedTheme = selectedTheme,
                consoleShaders = consoleShaders,
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

            // Only populate local DB if it's empty (first-device experience)
            val localMappings = database.spelaDatabaseQueries.getAllKeyMappings().executeAsList()
            if (localMappings.isNotEmpty()) return

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
                for ((retroButtonStr, keyCodeStr) in mapping.customMapping) {
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

    override fun getOrientationLock(): String {
        return database.spelaDatabaseQueries.getDeviceSetting("orientation_lock")
            .executeAsOneOrNull() ?: "auto"
    }

    override fun setOrientationLock(mode: String) {
        database.spelaDatabaseQueries.insertDeviceSetting("orientation_lock", mode)
    }

    override suspend fun pushKeyMappingsToServer() {
        runCatching {
            val allMappings = database.spelaDatabaseQueries.getAllKeyMappings().executeAsList()

            // Build global default mapping (consoleId == __default__)
            val globalMapping = allMappings
                .filter { it.console_id == DEFAULT_CONSOLE_ID }
                .associate { it.libretro_button_id.toString() to it.platform_key_code.toString() }

            // Build per-console mappings
            val consoleGroups = allMappings
                .filter { it.console_id != DEFAULT_CONSOLE_ID }
                .groupBy { it.console_id }

            val consoleMappings = consoleGroups.mapValues { (_, entities) ->
                ConsoleKeyMappingDto(
                    selectedMapping = "",
                    customMapping = entities.associate {
                        it.libretro_button_id.toString() to it.platform_key_code.toString()
                    },
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
}
