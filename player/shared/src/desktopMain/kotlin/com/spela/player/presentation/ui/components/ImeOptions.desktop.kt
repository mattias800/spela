package com.spela.player.presentation.ui.components

import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * Desktop has no fullscreen-IME problem — the software keyboard is
 * never invoked on a mouse-driven window. Return null and let Compose
 * use its defaults.
 */
actual fun noFullscreenImeOptions(): PlatformImeOptions? = null
