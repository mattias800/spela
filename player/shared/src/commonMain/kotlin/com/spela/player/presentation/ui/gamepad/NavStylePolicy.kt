package com.spela.player.presentation.ui.gamepad

import com.spela.player.libretro.ControllerStatusState

/**
 * Single source of truth for the navigation *style*: whether the UI shows the
 * gamepad style (L1/R1 section pill + focus navigation) or the touch/desktop
 * style (bottom tab bar / side rail).
 *
 * **Current policy — by control method in USE:** the gamepad style shows when
 * the last input came from a gamepad ([inputMode] == GAMEPAD); using the
 * keyboard, mouse, or touch shows the tab bar — regardless of whether a
 * controller is connected.
 *
 * This is intentionally the *only* place the policy lives so it's a one-line
 * change. Alternatives, for reference:
 *  - **Controller presence** (the #1187 behavior): `controllerStatus.connectedCount > 0`
 *  - **Presence AND usage:** `controllerStatus.connectedCount > 0 && inputMode == InputMode.GAMEPAD`
 *  - **Usage only** (current): `inputMode == InputMode.GAMEPAD`
 *
 * [controllerStatus] is passed in (unused by the current policy) so switching to
 * a presence-based policy needs no call-site changes.
 */
fun resolveGamepadNavStyle(
    inputMode: InputMode,
    @Suppress("UNUSED_PARAMETER") controllerStatus: ControllerStatusState,
): Boolean = inputMode == InputMode.GAMEPAD
