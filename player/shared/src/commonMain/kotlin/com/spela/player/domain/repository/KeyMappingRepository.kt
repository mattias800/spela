package com.spela.player.domain.repository

import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.domain.model.KeyMappingProfile

interface KeyMappingRepository {
    /**
     * Returns the stored mapping for a specific console and port.
     * Returns null if no custom mapping has been saved.
     */
    suspend fun getMappingForConsole(consoleId: String, port: Int = 0): KeyMappingProfile?

    /**
     * Sets a single button binding for a console and port.
     * If a binding for the same platform key already exists, it is replaced.
     */
    suspend fun setBinding(consoleId: String, port: Int, retroButtonId: Int, platformKeyCode: Int)

    /**
     * Removes all custom mappings for a console and port, reverting to defaults.
     */
    suspend fun resetToDefault(consoleId: String, port: Int = 0)

    /**
     * Removes a single button binding for a console and port.
     */
    suspend fun clearBinding(consoleId: String, port: Int, retroButtonId: Int)

    /**
     * Returns the effective mapping for a console, with fallback chain:
     * console-specific -> global default (__default__) -> hardcoded defaults.
     * The returned map is retroButtonId -> platformKeyCode.
     */
    suspend fun getEffectiveMapping(consoleId: String, port: Int = 0): Map<Int, Int>

    /**
     * Returns the hardcoded default mapping (retroButtonId -> platformKeyCode).
     * This is platform-specific and provided by the platform module.
     */
    fun getDefaultMapping(): Map<Int, Int>

    /** Returns the available presets for the current platform. */
    fun getAvailablePresets(): List<KeyMappingPreset>

    /** Applies a preset's bindings to the global default (__default__) console ID. */
    suspend fun applyPreset(presetId: String)

    /**
     * On first launch with no existing mappings, detects the device and applies
     * an appropriate preset. No-op if local DB already has mappings.
     */
    suspend fun ensureDefaultsApplied()

    /**
     * Returns the effective mapping for a specific game, with fallback chain:
     * per-game -> per-console -> global default -> hardcoded defaults.
     */
    suspend fun getEffectiveMappingForGame(gameId: String, consoleId: String, port: Int = 0): Map<Int, Int>

    /** Sets a per-game key mapping override. */
    suspend fun setGameMapping(gameId: String, bindings: Map<Int, Int>)

    /** Clears per-game key mapping override. */
    suspend fun clearGameMapping(gameId: String)

    /** Returns whether a game has a custom key mapping override. */
    suspend fun hasGameMapping(gameId: String): Boolean
}
