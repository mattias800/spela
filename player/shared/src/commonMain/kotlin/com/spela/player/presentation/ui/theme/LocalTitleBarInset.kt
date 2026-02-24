package com.spela.player.presentation.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Provides the macOS title bar inset height (28dp on macOS with fullWindowContent,
 * 0dp elsewhere). Used by SpTopBar and screens with floating top bars to avoid
 * overlapping with native traffic light buttons.
 */
val LocalTitleBarInset = compositionLocalOf { 0.dp }
