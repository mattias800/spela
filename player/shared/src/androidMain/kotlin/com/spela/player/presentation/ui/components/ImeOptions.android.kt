package com.spela.player.presentation.ui.components

import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * Android IME hint to dock the keyboard at the bottom of the screen
 * instead of going fullscreen. Gboard respects both tokens; the OR'd
 * pair covers OEM keyboards that only look for one name. Desktop /
 * iOS don't have this problem and return null from the `common`
 * side.
 */
actual fun noFullscreenImeOptions(): PlatformImeOptions? =
    PlatformImeOptions(privateImeOptions = "flagNoFullscreen,noFullscreenUI")
