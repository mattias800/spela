package com.spela.player.presentation.ui.screen

/**
 * Returns the platform-specific control hint text shown on first game launch.
 * On Android: describes touch controls.
 * On Desktop: describes keyboard controls.
 */
expect fun platformControlHintText(): String
