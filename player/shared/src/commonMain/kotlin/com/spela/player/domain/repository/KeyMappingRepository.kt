package com.spela.player.domain.repository

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
}
