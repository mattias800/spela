package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.ShaderPreviewDialog
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.presentation.viewmodel.ShaderScope
import com.spela.player.util.formatBytes

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit = {},
    onLogout: () -> Unit,
    onNavigateToConsoleSettings: (String) -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(SettingsIntent.LoadSettings)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(title = "Settings", showBack = true, onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Default,
            ),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            // Account section
            item {
                SettingsSectionHeader(title = "Account")
            }

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
            item {
                SettingsSectionHeader(title = "Color Theme")
            }

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
            item {
                SettingsSectionHeader(title = "Emulation")
            }

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

            // RetroAchievements section
            item {
                SettingsSectionHeader(title = "RetroAchievements")
            }

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
            item {
                SettingsSectionHeader(title = "Video Filter")
            }

            item {
                ShaderScopeTabs(
                    selectedScope = state.shaderScope,
                    onScopeChanged = { viewModel.onIntent(SettingsIntent.SwitchShaderScope(it)) },
                )
            }

            when (state.shaderScope) {
                ShaderScope.DEFAULT -> {
                    shaderDefaultScopeItems(state = state, viewModel = viewModel)
                }
                ShaderScope.PER_CONSOLE -> {
                    shaderPerConsoleScopeItems(
                        state = state,
                        onNavigateToConsoleSettings = onNavigateToConsoleSettings,
                    )
                }
            }

            // Storage section
            item {
                SettingsSectionHeader(title = "Storage")
            }

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

            // About section
            item {
                SettingsSectionHeader(title = "About")
            }

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
