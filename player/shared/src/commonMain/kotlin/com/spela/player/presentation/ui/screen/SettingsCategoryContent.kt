package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.components.SpSyncStatusIndicator
import com.spela.player.presentation.ui.feature.settings.DeviceManagementSection
import com.spela.player.presentation.ui.feature.settings.SettingsDivider
import com.spela.player.presentation.ui.feature.settings.SettingsInfoRow
import com.spela.player.presentation.ui.feature.settings.SettingsSectionHeader
import com.spela.player.presentation.ui.feature.settings.SettingsToggle
import com.spela.player.presentation.ui.feature.settings.controlsDefaultScopeItems
import com.spela.player.presentation.ui.feature.settings.shaderDefaultScopeItems
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.presentation.viewmodel.SettingsState
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.state.KeyMappingState
import com.spela.player.data.remote.SyncState
import com.spela.player.data.remote.ConnectionState
import com.spela.player.util.formatBytes

/**
 * Renders the content for a specific settings category inside a LazyColumn.
 */
@Composable
fun SettingsCategoryContent(
    category: SettingsCategory,
    state: SettingsState,
    viewModel: SettingsViewModel,
    syncState: SyncState,
    isOnline: Boolean,
    connectionState: ConnectionState,
    keyMappingViewModel: KeyMappingViewModel?,
    keyMappingState: KeyMappingState?,
    onNavigateToConsoleSettings: (String) -> Unit,
    onNavigateToLicenses: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SpSpacing.ScreenHorizontal,
            end = SpSpacing.ScreenHorizontal,
            top = topPadding + SpSpacing.Default,
            bottom = SpSpacing.Default,
        ),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        item {
            Text(
                text = category.label,
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )
            Spacer(Modifier.height(SpSpacing.Medium))
        }

        when (category) {
            SettingsCategory.GENERAL -> generalContent(state, viewModel)
            SettingsCategory.EMULATION -> emulationContent(state, viewModel)
            SettingsCategory.CONTROLS -> controlsContent(
                state, viewModel, keyMappingViewModel, keyMappingState, onNavigateToConsoleSettings,
            )
            SettingsCategory.ACHIEVEMENTS -> achievementsContent(state, viewModel)
            SettingsCategory.STORAGE_SYNC -> storageSyncContent(
                state, viewModel, syncState, isOnline, connectionState,
            )
            SettingsCategory.ABOUT -> aboutContent(
                state, viewModel, onNavigateToLicenses, onLogout,
            )
        }

        // Bottom spacing
        item { Spacer(Modifier.height(SpSpacing.XXXLarge)) }
    }
}

// --- Category content functions (LazyListScope extensions) ---

private fun androidx.compose.foundation.lazy.LazyListScope.generalContent(
    state: SettingsState,
    viewModel: SettingsViewModel,
) {
    // Color Theme
    item { SettingsSectionHeader(title = "Color Theme") }
    item {
        SpCard(onGradient = true) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = SpSpacing.Small),
            ) {
                ThemeOption.entries.forEachIndexed { index, option ->
                    SpRadioOption(
                        title = option.displayName,
                        description = option.description,
                        isSelected = state.selectedTheme == option.apiId,
                        onClick = { viewModel.onIntent(SettingsIntent.SelectTheme(option.apiId)) },
                    )
                    if (index < ThemeOption.entries.size - 1) SettingsDivider()
                }
            }
        }
    }

    // Display
    item { Spacer(Modifier.height(SpSpacing.Medium)) }
    item { SettingsSectionHeader(title = "Display") }
    item {
        SpCard(onGradient = true) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = SpSpacing.Small),
            ) {
                SpRadioOption(
                    title = "Auto",
                    description = "Follow system orientation",
                    isSelected = state.orientationLock == "auto",
                    onClick = { viewModel.onIntent(SettingsIntent.SetOrientationLock("auto")) },
                )
                SettingsDivider()
                SpRadioOption(
                    title = "Landscape",
                    description = "Lock to landscape orientation",
                    isSelected = state.orientationLock == "landscape",
                    onClick = { viewModel.onIntent(SettingsIntent.SetOrientationLock("landscape")) },
                )
                SettingsDivider()
                SpRadioOption(
                    title = "Portrait",
                    description = "Lock to portrait orientation",
                    isSelected = state.orientationLock == "portrait",
                    onClick = { viewModel.onIntent(SettingsIntent.SetOrientationLock("portrait")) },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.emulationContent(
    state: SettingsState,
    viewModel: SettingsViewModel,
) {
    // Emulation toggles
    item { SettingsSectionHeader(title = "Emulation") }
    item {
        SpCard(onGradient = true) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = SpSpacing.Small),
            ) {
                SettingsToggle(
                    title = "Performance Overlay",
                    subtitle = "Show FPS and frame time during gameplay",
                    isChecked = state.showPerformanceOverlay,
                    onToggle = { viewModel.onIntent(SettingsIntent.TogglePerformanceOverlay) },
                )
                SettingsDivider()
                SettingsToggle(
                    title = "Auto Save on Exit",
                    subtitle = "Automatically save progress when exiting a game",
                    isChecked = state.autoSaveEnabled,
                    onToggle = { viewModel.onIntent(SettingsIntent.ToggleAutoSave) },
                )
                SettingsDivider()
                SettingsToggle(
                    title = "Auto Load Save",
                    subtitle = "Resume from last save when starting a game",
                    isChecked = state.autoLoadSaveEnabled,
                    onToggle = { viewModel.onIntent(SettingsIntent.ToggleAutoLoadSave) },
                )
            }
        }
    }

    // Second Screen
    item { Spacer(Modifier.height(SpSpacing.Medium)) }
    item { SettingsSectionHeader(title = "Second Screen") }
    item {
        SpCard(onGradient = true) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = SpSpacing.Small),
            ) {
                SecondScreenPageOption.entries.forEachIndexed { index, option ->
                    SpRadioOption(
                        title = option.displayName,
                        description = option.description,
                        isSelected = state.defaultSecondScreenPage == option.apiId,
                        onClick = { viewModel.onIntent(SettingsIntent.SelectDefaultSecondScreenPage(option.apiId)) },
                    )
                    if (index < SecondScreenPageOption.entries.size - 1) SettingsDivider()
                }
            }
        }
    }

    // Video Filter
    item { Spacer(Modifier.height(SpSpacing.Medium)) }
    item { SettingsSectionHeader(title = "Video Filter") }
    shaderDefaultScopeItems(state = state, viewModel = viewModel)
}

private fun androidx.compose.foundation.lazy.LazyListScope.controlsContent(
    state: SettingsState,
    viewModel: SettingsViewModel,
    keyMappingViewModel: KeyMappingViewModel?,
    keyMappingState: KeyMappingState?,
    onNavigateToConsoleSettings: (String) -> Unit,
) {
    // Controls presets
    if (keyMappingViewModel != null && keyMappingState != null) {
        item { SettingsSectionHeader(title = "Default Controls") }
        controlsDefaultScopeItems(
            presets = keyMappingState.availablePresets,
            activePresetId = keyMappingState.activePresetId,
            onSelectPreset = { presetId ->
                keyMappingViewModel.onIntent(KeyMappingIntent.ApplyPreset(presetId))
            },
            onOpenFullMapping = {
                keyMappingViewModel.onIntent(KeyMappingIntent.ShowPresetPicker)
            },
        )
    }

    // Per-console settings
    if (state.consoles.isNotEmpty()) {
        item { Spacer(Modifier.height(SpSpacing.Medium)) }
        item { SettingsSectionHeader(title = "Per-console settings") }

        items(
            items = state.consoles,
            key = { "console_${it.id}" },
        ) { console ->
            SpCard(
                onClick = { onNavigateToConsoleSettings(console.id) },
                onGradient = true,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (console.iconUrl.isNotEmpty()) {
                        AsyncImage(
                            model = console.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            alpha = 0.7f,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.SportsEsports,
                            contentDescription = null,
                            tint = SpColor.OnBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.width(SpSpacing.Medium))
                    Text(
                        text = console.name,
                        style = SpTypography.TitleMedium,
                        color = SpColor.OnCard,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open ${console.name} settings",
                        tint = SpColor.OnBackgroundTertiary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.achievementsContent(
    state: SettingsState,
    viewModel: SettingsViewModel,
) {
    item { SettingsSectionHeader(title = "RetroAchievements") }
    item {
        val raStatus = state.raStatus
        SpCard(onGradient = true) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SpSpacing.Default),
            ) {
                if (raStatus != null && raStatus.linked) {
                    Text(
                        text = raStatus.username,
                        style = SpTypography.TitleLarge,
                        color = SpColor.OnCard,
                    )
                    Spacer(Modifier.height(SpSpacing.Small))
                    SettingsToggle(
                        title = "Hardcore Mode",
                        subtitle = "Disable save states and cheats for official leaderboards",
                        isChecked = raStatus.hardcoreEnabled,
                        onToggle = { viewModel.onIntent(SettingsIntent.ToggleRAHardcore) },
                    )
                    Spacer(Modifier.height(SpSpacing.Small))
                    SpSecondaryButton(
                        text = "Unlink Account",
                        onClick = { viewModel.onIntent(SettingsIntent.UnlinkRA) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "Link your RetroAchievements account to earn achievements while playing.",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundSecondary,
                    )
                    Spacer(Modifier.height(SpSpacing.Medium))
                    SpButton(
                        text = "Link Account",
                        onClick = { viewModel.onIntent(SettingsIntent.ShowRALinkDialog) },
                        style = SpButtonStyle.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.storageSyncContent(
    state: SettingsState,
    viewModel: SettingsViewModel,
    syncState: SyncState,
    isOnline: Boolean,
    connectionState: ConnectionState,
) {
    // Storage
    item { SettingsSectionHeader(title = "Storage") }
    item {
        SpCard(onGradient = true) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(SpSpacing.Default),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(text = "Local Cache", style = SpTypography.TitleMedium, color = SpColor.OnCard)
                    Text(
                        text = if (state.cacheSize <= 0) "Empty" else "${formatBytes(state.cacheSize)} used",
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
                SpButton(
                    text = "Clear",
                    onClick = { viewModel.onIntent(SettingsIntent.ShowClearCacheConfirm) },
                    style = SpButtonStyle.Ghost,
                )
            }
        }
    }

    // Devices
    item { Spacer(Modifier.height(SpSpacing.Medium)) }
    item { SettingsSectionHeader(title = "Devices") }
    item {
        DeviceManagementSection(
            devices = state.devices,
            isLoading = state.isLoadingDevices,
            showDeleteConfirmId = state.showDeleteDeviceConfirm,
            onRename = { deviceId, newName -> viewModel.onIntent(SettingsIntent.RenameDevice(deviceId, newName)) },
            onDelete = { deviceId -> viewModel.onIntent(SettingsIntent.DeleteDevice(deviceId)) },
            onShowDeleteConfirm = { deviceId -> viewModel.onIntent(SettingsIntent.ShowDeleteDeviceConfirm(deviceId)) },
            onDismissDeleteConfirm = { viewModel.onIntent(SettingsIntent.DismissDeleteDeviceConfirm) },
        )
    }

    // Sync
    item { Spacer(Modifier.height(SpSpacing.Medium)) }
    item { SettingsSectionHeader(title = "Sync") }
    item {
        SpCard(onGradient = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(SpSpacing.Default)) {
                SpSyncStatusIndicator(syncState = syncState, connectionState = connectionState)
                Spacer(Modifier.height(SpSpacing.Medium))
                SpSecondaryButton(
                    text = if (syncState.isSyncing) "Syncing..." else "Sync Now",
                    onClick = { viewModel.onIntent(SettingsIntent.SyncNow) },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = syncState.isSyncing,
                    enabled = isOnline && !syncState.isSyncing,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.aboutContent(
    state: SettingsState,
    viewModel: SettingsViewModel,
    onNavigateToLicenses: () -> Unit,
    onLogout: () -> Unit,
) {
    // About info
    item { SettingsSectionHeader(title = "About") }
    item {
        SpCard(onGradient = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(SpSpacing.Default)) {
                SettingsInfoRow(label = "Version", value = "1.0.0")
                Spacer(Modifier.height(SpSpacing.Small))
                SettingsInfoRow(label = "Build", value = "Compose Multiplatform")
            }
        }
    }
    item {
        SpCard(onClick = onNavigateToLicenses, onGradient = true) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(SpSpacing.Default),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "Credits & Licenses", style = SpTypography.TitleMedium, color = SpColor.OnCard)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open credits and licenses",
                    tint = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }

    // Device & Account
    item { Spacer(Modifier.height(SpSpacing.Medium)) }
    item { SettingsSectionHeader(title = "Device & Account") }
    item {
        SpCard(onGradient = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(SpSpacing.Default)) {
                SpTextField(
                    value = state.deviceName,
                    onValueChange = { viewModel.onIntent(SettingsIntent.UpdateDeviceName(it)) },
                    label = "Device Name",
                    placeholder = "My device",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(SpSpacing.Default))
                SpSecondaryButton(
                    text = "Sign Out",
                    onClick = { viewModel.onIntent(SettingsIntent.ShowLogoutConfirm) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
