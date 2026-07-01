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
import com.spela.player.presentation.ui.components.SpScreenContentList
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpImage
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
import com.spela.player.presentation.ui.feature.settings.saveStatePoliciesSectionItems
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
import com.spela.player.presentation.viewmodel.GamepadConfigViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.components.gamepad.ControllerControls
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.RightStickScroll
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import com.spela.player.util.currentPlatform
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
    gamepadConfigViewModel: GamepadConfigViewModel?,
    onNavigateToConsoleSettings: (String) -> Unit,
    onNavigateToControllerDetail: (Int) -> Unit,
    onNavigateToLicenses: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
) {
    val listState = rememberLazyListState()
    // Right analog stick scrolls the settings content in gamepad mode (#1362).
    RightStickScroll(listState)
    // Screen-scoped focus memory for the content pane (#1382), so e.g. drilling
    // into a console's settings and pressing B restores focus to that console row.
    val contentFocusMemory = rememberFocusMemoryState()
    CompositionLocalProvider(LocalFocusMemory provides contentFocusMemory) {
    SpScreenContentList(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("settings_category_content_list"),
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
                state, viewModel, keyMappingViewModel, keyMappingState, gamepadConfigViewModel,
                onNavigateToControllerDetail,
            )
            SettingsCategory.CONSOLES -> consolesContent(state, onNavigateToConsoleSettings)
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
    } // CompositionLocalProvider(LocalFocusMemory)
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
                SettingsDivider()
                SettingsToggle(
                    // #672 spec keys core_upd.settings.auto_update_label /
                    // _desc — the previous copy was technical and didn't
                    // make clear what "off" meant; the new copy aligns
                    // with the rest of the core-upgrade decision tone.
                    title = "Automatically update cores",
                    subtitle = "When off, we'll only switch cores when you say so.",
                    isChecked = state.autoUpdateCoresEnabled,
                    onToggle = { viewModel.onIntent(SettingsIntent.ToggleAutoUpdateCores) },
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

    // Save state opt-out overrides (only renders when the user has
    // at least one explicit override; no header otherwise so we don't
    // clutter the tab for the common case). See #804 phase 4b.
    if (state.consoleSaveStatePolicies.isNotEmpty()) {
        item { Spacer(Modifier.height(SpSpacing.Medium)) }
        item { SettingsSectionHeader(title = "Per-console save state overrides") }
        saveStatePoliciesSectionItems(state = state, viewModel = viewModel)
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
    gamepadConfigViewModel: GamepadConfigViewModel?,
    onNavigateToControllerDetail: (Int) -> Unit,
) {
    // Per-controller configuration (#1359): a list of connected controllers,
    // each drilling into a detail subscreen to edit its profile/type, assign or
    // clear its player number, and test its buttons live. This is the one global
    // place to verify "which controller do I have", set who's which player, and
    // correct a mis-detection (#1334 / #1353 / #1355). Shown on every platform.
    if (gamepadConfigViewModel != null) {
        item {
            val gamepadConfigState by gamepadConfigViewModel.state.collectAsState()
            SpCard(onGradient = true) {
                ControllerControls(
                    state = gamepadConfigState,
                    onSelectController = onNavigateToControllerDetail,
                )
            }
        }

        item {
            Text(
                text = "Gamepad button mappings are configured per console — open a console " +
                    "(Consoles tab) to view and change which physical button does what.",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.padding(
                    horizontal = SpSpacing.Default,
                    vertical = SpSpacing.Small,
                ),
            )
        }
    }

    // Keyboard key mapping (keycode presets). Desktop-only: there is no keyboard
    // on Android, and Android gamepad input is positional — so the keycode preset
    // UI would be empty/dead there (#1353).
    if (keyMappingViewModel != null && keyMappingState != null && currentPlatform() != "android") {
        item { SettingsSectionHeader(title = "Keyboard") }
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
}

private fun androidx.compose.foundation.lazy.LazyListScope.consolesContent(
    state: SettingsState,
    onNavigateToConsoleSettings: (String) -> Unit,
) {
    // Only list consoles the server actually has games for (#1383) — there's
    // nothing to configure for an empty console, and the empty-state below already
    // promises "with games".
    val consoles = state.consoles.filter { it.gameCount > 0 }.sortedForConsoleList()
    if (consoles.isNotEmpty()) {
        val firstConsoleId = consoles.first().id
        items(
            items = consoles,
            key = { "console_${it.id}" },
        ) { console ->
            SpCard(
                // Restore focus to this row on back from its ConsoleSettings, and
                // default-focus the first console on first entry (#1382).
                modifier = Modifier
                    .testTag("console_settings_row_${console.id}")
                    .focusRestoreItem(
                        key = "console_settings_${console.id}",
                        isDefault = console.id == firstConsoleId,
                    ),
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
                        SpImage(
                            model = console.iconUrl,
                            contentDescription = null,
                            // Eager load: the console list is short and bounded, so
                            // skip the request stagger to avoid logos popping in.
                            staggerMs = 0L,
                            modifier = Modifier.size(32.dp).alpha(0.7f),
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
    } else {
        item {
            Text(
                text = "No consoles with games found",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
            )
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
