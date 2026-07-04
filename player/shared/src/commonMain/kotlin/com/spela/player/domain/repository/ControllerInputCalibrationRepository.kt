package com.spela.player.domain.repository

import com.spela.player.domain.model.GamepadPosition

/**
 * Device-local, per-physical-controller input-layer calibration (#1341).
 *
 * The map is raw/reported [GamepadPosition] -> corrected [GamepadPosition].
 * It sits before the per-console mapping layer: platform input first normalizes
 * physical controls to a raw position, this calibration corrects bad controller
 * reports, then GamepadPosition -> RetroPad mapping decides what the core sees.
 *
 * Synchronous on purpose: input dispatch and [com.spela.player.libretro.GamepadPortManager]
 * consult this from controller/input paths, matching ControllerAssignmentRepository.
 */
interface ControllerInputCalibrationRepository {
    /** Returns raw position -> corrected position overrides for [stableKey]. */
    fun get(stableKey: String): Map<GamepadPosition, GamepadPosition>

    /** Stores [calibration] for [stableKey]. Empty maps clear the calibration. */
    fun put(stableKey: String, calibration: Map<GamepadPosition, GamepadPosition>)

    /** Removes all calibration for [stableKey]. */
    fun clear(stableKey: String)
}
