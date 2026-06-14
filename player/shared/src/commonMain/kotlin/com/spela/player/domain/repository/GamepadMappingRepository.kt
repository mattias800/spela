package com.spela.player.domain.repository

import com.spela.player.domain.model.GamepadPosition

/**
 * The **mapping layer** of the two-layer positional gamepad model (#1334):
 * `GamepadPosition` → libretro RetroPad id, per console/port. Brand-independent
 * — the input layer normalizes physical buttons to canonical positions first,
 * so the same positional binding works on any controller.
 *
 * Only diffs from [com.spela.player.domain.model.DefaultGamepadMapping] are
 * persisted; the effective mapping is the default with stored overrides layered
 * on top. Bindings are device-persisted now and become server-synced in the
 * Android phase (where the existing mappings are migrated into this layer).
 */
interface GamepadMappingRepository {
    /** Effective position→RetroPad for a console/port: defaults with stored overrides layered on top. */
    suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<GamepadPosition, Int>

    /** Persist one position→RetroPad binding (idempotent). */
    suspend fun setBinding(consoleId: String, port: Int, position: GamepadPosition, retroButtonId: Int)

    /** Clear all stored overrides for a console/port (revert to defaults). */
    suspend fun resetToDefault(consoleId: String, port: Int)

    /** The default position→RetroPad mapping, with no overrides. */
    fun getDefaultMapping(): Map<GamepadPosition, Int>
}
