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
import com.spela.player.presentation.ui.TestTags

/**
 * Categories for the settings list-detail layout. `testTag` is the
 * stable identifier tests should match — the display label can be
 * renamed or localised without breaking the suite.
 */
enum class SettingsCategory(
    val label: String,
    val icon: ImageVector,
    val testTag: String,
) {
    GENERAL("General", Icons.Filled.Settings, TestTags.SETTINGS_CATEGORY_GENERAL),
    EMULATION("Emulation", Icons.Filled.Build, TestTags.SETTINGS_CATEGORY_EMULATION),
    CONTROLS("Controls", Icons.Filled.Gamepad, TestTags.SETTINGS_CATEGORY_CONTROLS),
    CONSOLES("Per-Console", Icons.Filled.SportsEsports, TestTags.SETTINGS_CATEGORY_CONSOLES),
    ACHIEVEMENTS("Achievements", Icons.Filled.EmojiEvents, TestTags.SETTINGS_CATEGORY_ACHIEVEMENTS),
    STORAGE_SYNC("Storage & Sync", Icons.Filled.Storage, TestTags.SETTINGS_CATEGORY_STORAGE_SYNC),
    ABOUT("About", Icons.Filled.Info, TestTags.SETTINGS_CATEGORY_ABOUT),
}
