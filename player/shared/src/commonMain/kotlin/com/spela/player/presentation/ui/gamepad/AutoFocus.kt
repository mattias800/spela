package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.compositionLocalOf

// Historically this file also defined `Modifier.autoFocus()`, the legacy
// single-element default-focus modifier. Issue #1138 retired that primitive
// in favour of `Modifier.focusRestoreItem(key, isDefault = true)` (defined
// in FocusMemory.kt) which is a strict superset — default focus AND
// back-nav restoration via the same scope. The forward/tab-switch
// CompositionLocals stay here because they're still consumed by
// FocusMemory.kt and provided by SpelaApp on every screen mount.

/**
 * Whether the current screen was reached via forward navigation (not back/tab switch).
 * Set by SpelaApp when rendering each screen and consumed by
 * [Modifier.focusRestoreItem] to decide whether to restore the saved
 * focus key or fire the default-focus path.
 */
val LocalIsForwardNavigation = compositionLocalOf { false }

/**
 * Whether the current screen was reached via a bottom-nav tab switch
 * (L1/R1 on gamepads, or a tab tap on touch). Set by SpelaApp alongside
 * [LocalIsForwardNavigation] when rendering each screen.
 */
val LocalIsTabSwitch = compositionLocalOf { false }
