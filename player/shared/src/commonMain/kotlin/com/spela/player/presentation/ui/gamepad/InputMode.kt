package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.compositionLocalOf

enum class InputMode { TOUCH, GAMEPAD }

/**
 * Provides the current input mode to any composable in the tree.
 * Defaults to TOUCH so screens render normally without a gamepad.
 */
val LocalInputMode = compositionLocalOf { InputMode.TOUCH }
