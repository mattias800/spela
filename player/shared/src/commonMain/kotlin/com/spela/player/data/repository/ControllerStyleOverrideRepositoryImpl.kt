package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.ControllerStyle
import com.spela.player.domain.repository.ControllerStyleOverrideRepository

/**
 * SQLDelight-backed [ControllerStyleOverrideRepository] using the generic
 * device-local key-value store ([com.spela.player.SpelaDatabaseQueries]'s
 * `DeviceSettingEntity`). The key is namespaced per controller identity; the
 * value is the [ControllerStyle] enum name.
 *
 * Rows are tiny and reads hit a primary key, so we don't cache here — callers
 * that read in a hot loop (e.g. the gamepad-config refresh) cache the result
 * themselves.
 *
 * A stored value that no longer parses to a [ControllerStyle] (e.g. a renamed
 * enum constant from an old build) is treated as Auto rather than crashing.
 */
class ControllerStyleOverrideRepositoryImpl(
    database: SpelaDatabase,
) : ControllerStyleOverrideRepository {

    private val queries = database.spelaDatabaseQueries

    override suspend fun getOverride(deviceName: String): ControllerStyle? {
        val stored = queries.getDeviceSetting(key(deviceName)).executeAsOneOrNull() ?: return null
        return ControllerStyle.entries.firstOrNull { it.name == stored }
    }

    override suspend fun setOverride(deviceName: String, style: ControllerStyle?) {
        if (style == null) {
            queries.deleteDeviceSetting(key(deviceName))
        } else {
            queries.insertDeviceSetting(key(deviceName), style.name)
        }
    }

    private fun key(deviceName: String) = "$KEY_PREFIX$deviceName"

    private companion object {
        const val KEY_PREFIX = "controller_style_override:"
    }
}
