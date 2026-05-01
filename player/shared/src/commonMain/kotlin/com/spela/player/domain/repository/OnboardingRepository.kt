package com.spela.player.domain.repository

/**
 * Device-local "the user has seen and dismissed this hint" tracker (#861).
 *
 * Hint keys are free-form strings; convention is dotted, namespaced by
 * feature: `scummvm.trackpad.dragged`, `secondary.swipe.hint`,
 * `core.upgrade.chip`, etc. Backed by SQLDelight, so dismissals
 * persist across app launches but never sync to the server — these
 * are signals about device-specific affordances ("you've found the
 * trackpad on this device's bottom screen"), not about the user.
 *
 * The repository is intentionally narrow:
 *
 *   isDismissed(key)    → has the user seen + dismissed this hint?
 *   markDismissed(key)  → record that they have
 *   clearDismissed(key) → reset (rev-the-key pattern: when a hint's
 *                         copy changes substantially, bump the key
 *                         OR clear the row to re-onboard)
 */
interface OnboardingRepository {
    suspend fun isDismissed(key: String): Boolean
    suspend fun markDismissed(key: String)
    suspend fun clearDismissed(key: String)
}

/**
 * Stable hint-key constants. Adding a key here is the contract a
 * caller commits to: rendering the hint and dispatching
 * markDismissed when the user dismisses it.
 */
object OnboardingHintKeys {
    /**
     * Set the first time the user produces an onMouseMove event from
     * the secondary-screen trackpad while a ScummVM game is running.
     * While unset, the secondary screen overrides the user's default
     * landing page to land on Controls. Once set, the user's
     * preference is honored. See #861.
     */
    const val SCUMMVM_TRACKPAD_DRAGGED = "scummvm.trackpad.dragged"
}
