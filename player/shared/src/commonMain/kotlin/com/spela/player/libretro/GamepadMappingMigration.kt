package com.spela.player.libretro

import com.spela.player.domain.model.DEFAULT_CONSOLE_ID
import com.spela.player.domain.model.DefaultGamepadMapping
import com.spela.player.domain.model.GamepadPosition

/**
 * One-time, client-side migration (#1334) of legacy Android gamepad key-code
 * mappings into the positional mapping layer. The risky piece called out in the
 * spec — kept PURE and idempotent here so it's exhaustively desktop-testable.
 *
 * Rules:
 * - Per-console only (`console_id != __default__`): the global default isn't a
 *   user binding, and the positional default already reproduces it (this is also
 *   why "nintendo-standard" users see the documented behavior change).
 * - Only key codes that normalize to a [GamepadPosition] are converted (a
 *   keyboard key code on the wrong platform normalizes to null and is dropped).
 * - Only entries that DIFFER from [DefaultGamepadMapping] are emitted, so the
 *   positional layer stays clean (no redundant default-equal overrides) and
 *   genuine customizations are preserved.
 *
 * Idempotent: deterministic over its input, and the applier additionally skips
 * positions already present so re-running never clobbers a later user edit.
 */
object GamepadMappingMigration {
    data class LegacyRow(val consoleId: String, val port: Int, val keyCode: Int, val retroButtonId: Int)
    data class PositionalEntry(val consoleId: String, val port: Int, val position: GamepadPosition, val retroButtonId: Int)

    fun split(rows: List<LegacyRow>): List<PositionalEntry> =
        rows.filter { it.consoleId != DEFAULT_CONSOLE_ID }
            .mapNotNull { row ->
                val position = AndroidGamepadNormalizer.normalize(row.keyCode) ?: return@mapNotNull null
                if (DefaultGamepadMapping.POSITION_TO_RETRO[position] == row.retroButtonId) return@mapNotNull null
                PositionalEntry(row.consoleId, row.port, position, row.retroButtonId)
            }
}
