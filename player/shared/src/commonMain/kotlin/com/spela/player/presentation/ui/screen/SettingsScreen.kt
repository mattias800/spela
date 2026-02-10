package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLogout: () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(title = "Settings")

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
                        SpButton(
                            text = "Sign Out",
                            onClick = { viewModel.onIntent(SettingsIntent.ShowLogoutConfirm) },
                            style = SpButtonStyle.Outlined,
                            modifier = Modifier.fillMaxWidth(),
                        )
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

            // Shader section
            item {
                SettingsSectionHeader(title = "Video Filter")
            }

            item {
                SpCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SpSpacing.Small),
                    ) {
                        ShaderPreset.entries.forEachIndexed { index, shader ->
                            ShaderOption(
                                shader = shader,
                                isSelected = state.selectedShader == shader,
                                onClick = { viewModel.onIntent(SettingsIntent.SelectShader(shader)) },
                            )
                            if (index < ShaderPreset.entries.size - 1) {
                                SettingsDivider()
                            }
                        }
                    }
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
                                text = formatSettingsCacheSize(state.cacheSize),
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

            // Bottom spacing
            item {
                Spacer(Modifier.height(SpSpacing.XXXLarge))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = SpTypography.HeadlineSmall,
        color = SpColor.OnBackground,
        modifier = Modifier
            .padding(top = SpSpacing.Small)
            .semantics { heading() },
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .focusable()
            .semantics {
                contentDescription = "$title, $subtitle"
                role = Role.Switch
                stateDescription = if (isChecked) "On" else "Off"
            }
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SpTypography.TitleMedium,
                color = SpColor.OnCard,
            )
            Text(
                text = subtitle,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
        Spacer(Modifier.width(SpSpacing.Medium))
        Switch(
            checked = isChecked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SpColor.OnPrimary,
                checkedTrackColor = SpColor.Primary,
                uncheckedThumbColor = SpColor.OnBackgroundTertiary,
                uncheckedTrackColor = SpColor.SurfaceBright,
            ),
        )
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = SpSpacing.Default)
            .background(SpColor.Divider),
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
        Text(
            text = value,
            style = SpTypography.BodyMedium,
            color = SpColor.OnCard,
        )
    }
}

@Composable
private fun ShaderOption(
    shader: ShaderPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .focusable()
            .semantics {
                contentDescription = "${shader.displayName}, ${shader.description}"
                role = Role.RadioButton
                stateDescription = if (isSelected) "Selected" else "Not selected"
            }
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shader.displayName,
                style = SpTypography.TitleMedium,
                color = SpColor.OnCard,
            )
            Text(
                text = shader.description,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
        Spacer(Modifier.width(SpSpacing.Medium))
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = SpColor.Primary,
                unselectedColor = SpColor.OnBackgroundTertiary,
            ),
        )
    }
}

private fun formatSettingsCacheSize(bytes: Long): String {
    if (bytes <= 0) return "Empty"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s used".format(value, units[unitIndex])
}
