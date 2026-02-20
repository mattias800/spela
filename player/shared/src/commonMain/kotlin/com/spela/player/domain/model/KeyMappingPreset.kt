package com.spela.player.domain.model

/**
 * A bundled preset that maps all retroButtonIds to platform key codes.
 * Users can apply a preset to set all mappings with one tap.
 */
data class KeyMappingPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val bindings: Map<Int, Int>, // retroButtonId -> platformKeyCode
)

/**
 * Platform-specific preset definitions.
 * Each platform provides its own presets using native key codes.
 */
expect fun getPlatformPresets(): List<KeyMappingPreset>
