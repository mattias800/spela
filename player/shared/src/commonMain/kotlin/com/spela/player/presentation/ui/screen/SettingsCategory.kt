package com.spela.player.presentation.ui.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Categories for the settings list-detail layout.
 */
enum class SettingsCategory(
    val label: String,
    val icon: ImageVector,
) {
    GENERAL("General", Icons.Filled.Settings),
    EMULATION("Emulation", Icons.Filled.Build),
    CONTROLS("Controls", Icons.Filled.Gamepad),
    CONSOLES("Per-Console", Icons.Filled.SportsEsports),
    ACHIEVEMENTS("Achievements", Icons.Filled.EmojiEvents),
    STORAGE_SYNC("Storage & Sync", Icons.Filled.Storage),
    ABOUT("About", Icons.Filled.Info),
}
