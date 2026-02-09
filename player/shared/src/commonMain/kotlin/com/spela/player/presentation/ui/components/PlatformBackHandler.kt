package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable

/**
 * Platform-specific back button handler.
 * On Android, intercepts the system back button.
 * On Desktop, this is a no-op (no system back button).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
