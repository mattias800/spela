package com.spela.player.domain.repository

/**
 * Device-local, per-physical-controller player-slot assignment (#1359). Keyed by
 * a stable controller id (Android `InputDevice.descriptor`; desktop SDL device
 * name), so a pad keeps its player number across reconnects and app restarts.
 * Never synced — which physical pad is which player is a per-device concern.
 *
 * **Synchronous on purpose:** [com.spela.player.libretro.GamepadPortManager] reads
 * this from inside its synchronized critical sections (on the input threads) when
 * a device connects, so it must not suspend. The table is a tiny primary-key
 * store, so a synchronous SQLDelight read/write is cheap.
 *
 * Semantics: a key absent from [getAll] means **never seen** (the manager
 * auto-claims the lowest free slot); a key present with a `null` slot means
 * **explicitly cleared** (remembered as "no player").
 */
interface ControllerAssignmentRepository {
    /** All remembered assignments: stableKey -> slot (0-based), or `null` slot when cleared. */
    fun getAll(): Map<String, Int?>

    /** Remember [slot] (0-based player port) for [stableKey], or `null` to mark it cleared. */
    fun put(stableKey: String, slot: Int?)
}
