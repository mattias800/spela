package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsState
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.presentation.viewmodel.ShaderScope

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
                ShaderScopeTabs(
                    selectedScope = state.shaderScope,
                    onScopeChanged = { viewModel.onIntent(SettingsIntent.SwitchShaderScope(it)) },
                )
            }

            when (state.shaderScope) {
                ShaderScope.DEFAULT -> {
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
                }
                ShaderScope.PER_CONSOLE -> {
                    if (state.consoles.isEmpty()) {
                        item {
                            SpCard {
                                Text(
                                    text = "No consoles available",
                                    style = SpTypography.BodyMedium,
                                    color = SpColor.OnBackgroundTertiary,
                                    modifier = Modifier.padding(SpSpacing.Default),
                                )
                            }
                        }
                    } else {
                        items(
                            items = state.consoles,
                            key = { it.id },
                        ) { console ->
                            ConsoleShaderCard(
                                console = console,
                                currentShader = state.consoleShaders[console.id]
                                    ?: state.selectedShader,
                                isExpanded = state.expandedConsoleId == console.id,
                                hasDeviceOverride = state.deviceShaderOverrides.containsKey(console.id),
                                deviceOverrideShader = state.deviceShaderOverrides[console.id],
                                globalDefault = state.selectedShader,
                                onExpand = { viewModel.onIntent(SettingsIntent.ExpandConsole(console.id)) },
                                onSelectShader = { shader ->
                                    viewModel.onIntent(SettingsIntent.SelectConsoleShader(console.id, shader))
                                },
                                onToggleDeviceOverride = { enabled ->
                                    if (enabled) {
                                        val current = state.consoleShaders[console.id]
                                            ?: state.selectedShader
                                        viewModel.onIntent(SettingsIntent.SetDeviceOverride(console.id, current))
                                    } else {
                                        viewModel.onIntent(SettingsIntent.SetDeviceOverride(console.id, null))
                                    }
                                },
                                onSelectDeviceShader = { shader ->
                                    viewModel.onIntent(SettingsIntent.SetDeviceOverride(console.id, shader))
                                },
                            )
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
private fun ShaderScopeTabs(
    selectedScope: ShaderScope,
    onScopeChanged: (ShaderScope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        ShaderScope.entries.forEach { scope ->
            val isSelected = scope == selectedScope
            val label = when (scope) {
                ShaderScope.DEFAULT -> "Default"
                ShaderScope.PER_CONSOLE -> "Per Console"
            }
            val backgroundColor = if (isSelected) {
                SpColor.Primary.copy(alpha = 0.2f)
            } else {
                SpColor.SurfaceVariant
            }
            val textColor = if (isSelected) SpColor.Primary else SpColor.OnBackgroundSecondary

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .clickable { onScopeChanged(scope) }
                    .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = SpTypography.LabelMedium,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun ConsoleShaderCard(
    console: Console,
    currentShader: ShaderPreset,
    isExpanded: Boolean,
    hasDeviceOverride: Boolean,
    deviceOverrideShader: ShaderPreset?,
    globalDefault: ShaderPreset,
    onExpand: () -> Unit,
    onSelectShader: (ShaderPreset) -> Unit,
    onToggleDeviceOverride: (Boolean) -> Unit,
    onSelectDeviceShader: (ShaderPreset) -> Unit,
) {
    val effectiveShader = deviceOverrideShader ?: currentShader

    SpCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Console header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(SpSpacing.Default),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = console.name,
                        style = SpTypography.TitleMedium,
                        color = SpColor.OnCard,
                    )
                    Text(
                        text = effectiveShader.displayName +
                            if (hasDeviceOverride) " (device)" else "",
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
                Text(
                    text = if (isExpanded) "\u25B2" else "\u25BC",
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnBackgroundTertiary,
                )
            }

            // Expanded shader picker
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SettingsDivider()

                    // "Use Default" option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectShader(globalDefault) }
                            .focusable()
                            .semantics {
                                contentDescription = "Use Default (${globalDefault.displayName})"
                                role = Role.RadioButton
                                stateDescription = if (currentShader == globalDefault && !hasDeviceOverride) {
                                    "Selected"
                                } else {
                                    "Not selected"
                                }
                            }
                            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use Default",
                                style = SpTypography.TitleMedium,
                                color = SpColor.Accent,
                            )
                            Text(
                                text = "Currently: ${globalDefault.displayName}",
                                style = SpTypography.BodySmall,
                                color = SpColor.OnBackgroundTertiary,
                            )
                        }
                    }

                    SettingsDivider()

                    // Shader options
                    ShaderPreset.entries.forEachIndexed { index, shader ->
                        ShaderOption(
                            shader = shader,
                            isSelected = currentShader == shader,
                            onClick = { onSelectShader(shader) },
                        )
                        if (index < ShaderPreset.entries.size - 1) {
                            SettingsDivider()
                        }
                    }

                    SettingsDivider()

                    // Device override checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleDeviceOverride(!hasDeviceOverride) }
                            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Override on this device only",
                                style = SpTypography.TitleMedium,
                                color = SpColor.OnCard,
                            )
                            Text(
                                text = "Uses a local override instead of the server setting",
                                style = SpTypography.BodySmall,
                                color = SpColor.OnBackgroundTertiary,
                            )
                        }
                        Spacer(Modifier.width(SpSpacing.Medium))
                        Checkbox(
                            checked = hasDeviceOverride,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = SpColor.Primary,
                                uncheckedColor = SpColor.OnBackgroundTertiary,
                                checkmarkColor = SpColor.OnPrimary,
                            ),
                        )
                    }

                    // Device override shader picker (shown when device override is active)
                    AnimatedVisibility(
                        visible = hasDeviceOverride,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpColor.SurfaceVariant.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                text = "Device Shader",
                                style = SpTypography.LabelMedium,
                                color = SpColor.OnBackgroundSecondary,
                                modifier = Modifier.padding(
                                    start = SpSpacing.Default,
                                    top = SpSpacing.Medium,
                                    bottom = SpSpacing.XSmall,
                                ),
                            )
                            ShaderPreset.entries.forEachIndexed { index, shader ->
                                ShaderOption(
                                    shader = shader,
                                    isSelected = deviceOverrideShader == shader,
                                    onClick = { onSelectDeviceShader(shader) },
                                )
                                if (index < ShaderPreset.entries.size - 1) {
                                    SettingsDivider()
                                }
                            }
                        }
                    }
                }
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
