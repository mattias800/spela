package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.ShaderPreviewDialog
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.feature.library.darken
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.feature.settings.DeviceManagementSection
import com.spela.player.presentation.ui.feature.settings.SettingsDivider
import com.spela.player.presentation.ui.feature.settings.SettingsInfoRow
import com.spela.player.presentation.ui.feature.settings.SettingsSectionHeader
import com.spela.player.presentation.ui.feature.settings.SettingsToggle
import com.spela.player.presentation.ui.feature.settings.controlsDefaultScopeItems
import com.spela.player.presentation.ui.feature.settings.shaderDefaultScopeItems
import com.spela.player.presentation.ui.components.keymapping.PresetPickerDialog
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.ui.components.SpSyncStatusIndicator
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.util.formatBytes

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    keyMappingViewModel: KeyMappingViewModel? = null,
    onBack: () -> Unit = {},
    onLogout: () -> Unit,
    onNavigateToConsoleSettings: (String) -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val keyMappingState = keyMappingViewModel?.state?.collectAsState()

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = state.scrollIndex,
        initialFirstVisibleItemScrollOffset = state.scrollOffset,
    )

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        viewModel.onIntent(
            SettingsIntent.SaveScrollPosition(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.onIntent(SettingsIntent.LoadSettings)
        keyMappingViewModel?.onIntent(KeyMappingIntent.LoadMapping("__default__"))
    }

    // Dialogs
    if (state.showLogoutConfirm) {
        SpConfirmDialog(
            title = "Sign Out",
            message = "Are you sure you want to sign out? You'll need to re-enter your credentials.",
            onDismiss = { viewModel.onIntent(SettingsIntent.DismissLogoutConfirm) },
            onConfirm = {
                viewModel.onIntent(SettingsIntent.Logout)
                onLogout()
            },
            confirmText = "Sign Out",
            isDestructive = true,
        )
    }

    if (state.showClearCacheConfirm) {
        SpConfirmDialog(
            title = "Clear Cache",
            message = "This will remove all downloaded games and cores. You'll need to re-download them to play.",
            onDismiss = { viewModel.onIntent(SettingsIntent.DismissClearCacheConfirm) },
            onConfirm = { viewModel.onIntent(SettingsIntent.ClearCache) },
            confirmText = "Clear",
            isDestructive = true,
        )
    }

    if (state.showRALinkDialog) {
        var raUsername by remember { mutableStateOf("") }
        var raPassword by remember { mutableStateOf("") }

        SpDialog(
            title = "Link RetroAchievements",
            onDismiss = { viewModel.onIntent(SettingsIntent.DismissRALinkDialog) },
            onConfirm = { viewModel.onIntent(SettingsIntent.LinkRA(raUsername, raPassword)) },
            confirmText = if (state.raLinkLoading) "Linking..." else "Link",
            isLoading = state.raLinkLoading,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium)) {
                SpTextField(
                    value = raUsername,
                    onValueChange = { raUsername = it },
                    label = "Username",
                    placeholder = "RetroAchievements username",
                    modifier = Modifier.fillMaxWidth(),
                )
                SpTextField(
                    value = raPassword,
                    onValueChange = { raPassword = it },
                    label = "Password",
                    placeholder = "RetroAchievements password",
                    isPassword = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val raLinkError = state.raLinkError
                if (raLinkError != null) {
                    Text(
                        text = raLinkError,
                        style = SpTypography.BodySmall,
                        color = SpColor.Error,
                    )
                }
                Text(
                    text = "Your credentials are sent to RetroAchievements to obtain an API token. Your password is not stored.",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }
    }

    val titleBarInset = LocalTitleBarInset.current
    val gradientColors = listOf(
        SpColor.PrimaryDark.darken(0.74f),
        SpColor.SecondaryDark.darken(0.82f),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val d = (size.width + size.height) * 0.25f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset(cx - d, cy - d),
                        end = Offset(cx + d, cy + d),
                    ),
                )
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_list"),
            contentPadding = PaddingValues(
                start = SpSpacing.ScreenHorizontal,
                end = SpSpacing.ScreenHorizontal,
                top = titleBarInset + SpSpacing.Default,
                bottom = SpSpacing.Default,
            ),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            // Account section
            item { SettingsSectionHeader(title = "Account") }

            item {
                SpCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpSpacing.Default),
                    ) {
                        if (state.username.isNotEmpty()) {
                            Text(
                                text = state.username,
                                style = SpTypography.TitleLarge,
                                color = SpColor.OnCard,
                            )
                            Spacer(Modifier.height(SpSpacing.XSmall))
                            Text(
                                text = state.serverUrl.ifEmpty { "Connected" },
                                style = SpTypography.BodySmall,
                                color = SpColor.OnBackgroundTertiary,
                            )
                        }
                        Spacer(Modifier.height(SpSpacing.Default))
                        SpTextField(
                            value = state.deviceName,
                            onValueChange = { viewModel.onIntent(SettingsIntent.UpdateDeviceName(it)) },
                            label = "Device Name",
                            placeholder = "My device",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(SpSpacing.Default))
                        SpButton(
                            text = "Sign Out",
                            onClick = { viewModel.onIntent(SettingsIntent.ShowLogoutConfirm) },
                            style = SpButtonStyle.Outlined,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Color Theme section
            item { Spacer(Modifier.height(SpSpacing.Medium)) }
            item { SettingsSectionHeader(title = "Color Theme") }

            item {
                SpCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SpSpacing.Small),
                    ) {
                        ThemeOption.entries.forEachIndexed { index, option ->
                            SpRadioOption(
                                title = option.displayName,
                                description = option.description,
                                isSelected = state.selectedTheme == option.apiId,
                                onClick = { viewModel.onIntent(SettingsIntent.SelectTheme(option.apiId)) },
                            )
                            if (index < ThemeOption.entries.size - 1) {
                                SettingsDivider()
                            }
                        }
                    }
                }
            }

            // Emulation section
            item { Spacer(Modifier.height(SpSpacing.Medium)) }
            item { SettingsSectionHeader(title = "Emulation") }

            item {
                SpCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SpSpacing.Small),
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

            // Controls section
            if (keyMappingViewModel != null && keyMappingState != null) {
                item { Spacer(Modifier.height(SpSpacing.Medium)) }
                item { SettingsSectionHeader(title = "Controls") }

                controlsDefaultScopeItems(
                    presets = keyMappingState.value.availablePresets,
                    activePresetId = keyMappingState.value.activePresetId,
                    onSelectPreset = { presetId ->
                        keyMappingViewModel.onIntent(KeyMappingIntent.ApplyPreset(presetId))
                    },
                    onOpenFullMapping = {
                        keyMappingViewModel.onIntent(KeyMappingIntent.ShowPresetPicker)
                    },
                )
            }

            // Console settings section
            if (state.consoles.isNotEmpty()) {
                item { Spacer(Modifier.height(SpSpacing.Medium)) }
                item { SettingsSectionHeader(title = "Per-console settings") }

                items(
                    items = state.consoles,
                    key = { "console_${it.id}" },
                ) { console ->
                    SpCard(
                        onClick = { onNavigateToConsoleSettings(console.id) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = SpSpacing.Default,
                                    vertical = SpSpacing.Medium,
                                ),
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

            // RetroAchievements section
            item { Spacer(Modifier.height(SpSpacing.Medium)) }
            item { SettingsSectionHeader(title = "RetroAchievements") }

            item {
                val raStatus = state.raStatus
                SpCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpSpacing.Default),
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
                            SpButton(
                                text = "Unlink Account",
                                onClick = { viewModel.onIntent(SettingsIntent.UnlinkRA) },
                                style = SpButtonStyle.Outlined,
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

            // Shader section
            item { Spacer(Modifier.height(SpSpacing.Medium)) }
            item { SettingsSectionHeader(title = "Video Filter") }

            shaderDefaultScopeItems(state = state, viewModel = viewModel)

            // Storage section
            item { Spacer(Modifier.height(SpSpacing.Medium)) }
            item { SettingsSectionHeader(title = "Storage") }

            item {
                SpCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpSpacing.Default),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Local Cache",
                                style = SpTypography.TitleMedium,
                                color = SpColor.OnCard,
                            )
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

            // Devices section
            item { Spacer(Modifier.height(SpSpacing.Medium)) }
            item { SettingsSectionHeader(title = "Devices") }

            item {
                DeviceManagementSection(
                    devices = state.devices,
                    isLoading = state.isLoadingDevices,
                    showDeleteConfirmId = state.showDeleteDeviceConfirm,
                    onRename = { deviceId, newName ->
                        viewModel.onIntent(SettingsIntent.RenameDevice(deviceId, newName))
                    },
                    onDelete = { deviceId ->
                        viewModel.onIntent(SettingsIntent.DeleteDevice(deviceId))
                    },
                    onShowDeleteConfirm = { deviceId ->
                        viewModel.onIntent(SettingsIntent.ShowDeleteDeviceConfirm(deviceId))
                    },
                    onDismissDeleteConfirm = {
                        viewModel.onIntent(SettingsIntent.DismissDeleteDeviceConfirm)
                    },
                )
            }

            // Sync section
            item { Spacer(Modifier.height(SpSpacing.Medium)) }
            item { SettingsSectionHeader(title = "Sync") }

            item {
                SpCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpSpacing.Default),
                    ) {
                        SpSyncStatusIndicator(
                            syncState = syncState,
                            isOnline = isOnline,
                        )
                        Spacer(Modifier.height(SpSpacing.Medium))
                        SpButton(
                            text = if (syncState.isSyncing) "Syncing..." else "Sync Now",
                            onClick = { viewModel.onIntent(SettingsIntent.SyncNow) },
                            style = SpButtonStyle.Outlined,
                            modifier = Modifier.fillMaxWidth(),
                            isLoading = syncState.isSyncing,
                            enabled = isOnline && !syncState.isSyncing,
                        )
                    }
                }
            }

            // About section
            item { Spacer(Modifier.height(SpSpacing.Medium)) }
            item { SettingsSectionHeader(title = "About") }

            item {
                SpCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpSpacing.Default),
                    ) {
                        SettingsInfoRow(label = "Version", value = "1.0.0")
                        Spacer(Modifier.height(SpSpacing.Small))
                        SettingsInfoRow(label = "Build", value = "Compose Multiplatform")
                    }
                }
            }

            item {
                SpCard(
                    onClick = onNavigateToLicenses,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpSpacing.Default),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Credits & Licenses",
                            style = SpTypography.TitleMedium,
                            color = SpColor.OnCard,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open credits and licenses",
                            tint = SpColor.OnBackgroundTertiary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // Bottom spacing
            item {
                Spacer(Modifier.height(SpSpacing.XXXLarge))
            }
        }
    }

    // Preset picker dialog for controls section
    if (keyMappingState?.value?.showPresetPicker == true && keyMappingViewModel != null) {
        PresetPickerDialog(
            presets = keyMappingState.value.availablePresets,
            activePresetId = keyMappingState.value.activePresetId,
            onSelectPreset = { presetId ->
                keyMappingViewModel.onIntent(KeyMappingIntent.ApplyPreset(presetId))
            },
            onDismiss = {
                keyMappingViewModel.onIntent(KeyMappingIntent.DismissPresetPicker)
            },
        )
    }

    ShaderPreviewDialog(
        visible = state.fullscreenPreviewConsoleId != null,
        imageUrl = state.fullscreenPreviewConsoleId?.let { consoleId ->
            "${state.serverUrl}/api/consoles/${consoleId}/preview-screenshot"
        },
        shader = state.fullscreenPreviewConsoleId?.let { consoleId ->
            state.deviceShaderOverrides[consoleId]
                ?: state.consoleShaders[consoleId]
                ?: state.selectedShader
        } ?: state.selectedShader,
        onDismiss = { viewModel.onIntent(SettingsIntent.DismissShaderPreviewFullscreen) },
    )
}

internal enum class ThemeOption(
    val apiId: String,
    val displayName: String,
    val description: String,
) {
    DEFAULT_DARK("default-dark", "Default Dark", "Current dark theme"),
    DEFAULT_LIGHT("default-light", "Default Light", "Clean light theme"),
    NINTENDO_COLORFUL("nintendo-colorful", "Nintendo Colorful", "Bright, joyful, retro"),
    OCEAN_DARK("ocean-dark", "Ocean Dark", "Deep blue dark theme"),
    SUNSET_WARM("sunset-warm", "Sunset Warm", "Warm amber light theme"),
}
