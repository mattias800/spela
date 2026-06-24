package com.spela.player.presentation.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-level "quit the whole application" hook.
 *
 * Desktop provides it (from `Main.kt`) with a handler that runs the clean
 * shutdown — stop a running game (auto-save + core teardown) then
 * `exitApplication`. It is **null on Android**, where the OS owns app
 * lifecycle (home / back / recents), so the in-app Quit affordance is hidden
 * there.
 *
 * The motivating case is Steam Deck Gaming Mode (and any controller-only
 * fullscreen handheld): there is no window chrome / Alt+F4, so a
 * gamepad-reachable in-app Quit is the only clean way out. See #1439.
 */
val LocalAppQuit = staticCompositionLocalOf<(() -> Unit)?> { null }
