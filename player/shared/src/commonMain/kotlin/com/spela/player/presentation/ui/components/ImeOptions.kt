package com.spela.player.presentation.ui.components

import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * Platform-specific `PlatformImeOptions` that disable the Android
 * IME's fullscreen "extract view" mode. Gboard and most OEM keyboards
 * respect `privateImeOptions = "flagNoFullscreen,noFullscreenUI"`;
 * when set, the keyboard docks at the bottom instead of taking over
 * the screen.
 *
 * Applied by [SpTextField] so it lands on every text input in the
 * app from a single source of truth.
 *
 * On desktop / iOS the platform IME type has no constructor args, so
 * we return null and let Compose use defaults.
 */
expect fun noFullscreenImeOptions(): PlatformImeOptions?
