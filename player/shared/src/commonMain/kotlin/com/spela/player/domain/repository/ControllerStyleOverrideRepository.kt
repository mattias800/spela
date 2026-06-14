package com.spela.player.domain.repository

import com.spela.player.domain.model.ControllerStyle

/**
 * Device-local, per-controller "this controller is actually a `<style>`"
 * override (#1334, component D). Detection (SDL gamepad type / vendor-product)
 * is usually right, but when it isn't the user can correct it here. Keyed by
 * the controller's identity (its OS device name) so two pads of the same model
 * share the correction.
 *
 * Never synced: calibrating the physical pad in hand is a device-local concern,
 * exactly like the device shader override. Backed by the generic
 * `DeviceSettingEntity` key-value store (no dedicated table / migration).
 *
 * Absence of an override means **Auto** — defer to detection. Storing a style
 * (including [ControllerStyle.Generic]) is an explicit user choice and is
 * distinct from Auto.
 */
interface ControllerStyleOverrideRepository {
    /** The stored override for this controller, or null when Auto (no override). */
    suspend fun getOverride(deviceName: String): ControllerStyle?

    /** Store [style] as the override, or pass null to clear it (back to Auto). */
    suspend fun setOverride(deviceName: String, style: ControllerStyle?)
}
