package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.repository.ControllerInputCalibrationRepository

/**
 * SQLDelight-backed controller input calibration store using DeviceSettingEntity.
 * Values are small enum-name pairs, encoded as `RAW=TARGET;RAW=TARGET`.
 */
class ControllerInputCalibrationRepositoryImpl(
    database: SpelaDatabase,
) : ControllerInputCalibrationRepository {

    private val queries = database.spelaDatabaseQueries

    override fun get(stableKey: String): Map<GamepadPosition, GamepadPosition> {
        if (stableKey.isBlank()) return emptyMap()
        val stored = queries.getDeviceSetting(key(stableKey)).executeAsOneOrNull() ?: return emptyMap()
        return decode(stored)
    }

    override fun put(stableKey: String, calibration: Map<GamepadPosition, GamepadPosition>) {
        if (stableKey.isBlank()) return
        val normalized = calibration.filter { (raw, target) -> raw != target }
        if (normalized.isEmpty()) {
            clear(stableKey)
        } else {
            queries.insertDeviceSetting(key(stableKey), encode(normalized))
        }
    }

    override fun clear(stableKey: String) {
        if (stableKey.isBlank()) return
        queries.deleteDeviceSetting(key(stableKey))
    }

    private fun encode(calibration: Map<GamepadPosition, GamepadPosition>): String =
        calibration.entries
            .sortedBy { it.key.ordinal }
            .joinToString(";") { (raw, target) -> "${raw.name}=${target.name}" }

    private fun decode(value: String): Map<GamepadPosition, GamepadPosition> {
        val result = linkedMapOf<GamepadPosition, GamepadPosition>()
        value.split(';')
            .asSequence()
            .filter { it.isNotBlank() }
            .forEach { pair ->
                val rawName = pair.substringBefore('=', missingDelimiterValue = "")
                val targetName = pair.substringAfter('=', missingDelimiterValue = "")
                val raw = GamepadPosition.entries.firstOrNull { it.name == rawName } ?: return@forEach
                val target = GamepadPosition.entries.firstOrNull { it.name == targetName } ?: return@forEach
                if (raw != target) result[raw] = target
            }
        return result
    }

    private fun key(stableKey: String) = "$KEY_PREFIX$stableKey"

    private companion object {
        const val KEY_PREFIX = "controller_input_calibration:"
    }
}
