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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Console
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.components.ShaderPreview
import com.spela.player.presentation.ui.components.ShaderPreviewDialog
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpTextField
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
    onBack: () -> Unit = {},
    onLogout: () -> Unit,
    onNavigateToConsoleSettings: (String) -> Unit = {},
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

                    item {
                        val previewConsoleId = state.consoles.firstOrNull()?.id
                        if (previewConsoleId != null) {
                            ShaderPreview(
                                imageUrl = "${state.serverUrl}/api/consoles/${previewConsoleId}/preview-screenshot",
                                shader = state.selectedShader,
                                onClick = { viewModel.onIntent(SettingsIntent.ShowShaderPreviewFullscreen(previewConsoleId)) },
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.Default)
                                    .widthIn(max = 360.dp),
                            )
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
                            val consoleShader = state.consoleShaders[console.id]
                                ?: state.selectedShader
                            val hasOverride = state.deviceShaderOverrides.containsKey(console.id)
                            val effectiveShader = state.deviceShaderOverrides[console.id]
                                ?: consoleShader

                            SpCard(
                                onClick = { onNavigateToConsoleSettings(console.id) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
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
                                                if (hasOverride) " (device)" else "",
                                            style = SpTypography.BodySmall,
                                            color = SpColor.OnBackgroundTertiary,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Open console settings",
                                        tint = SpColor.OnBackgroundTertiary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
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
                    .spFocusRing(shape = RoundedCornerShape(20.dp))
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
    previewUrl: String?,
    onExpand: () -> Unit,
    onSelectShader: (ShaderPreset) -> Unit,
    onToggleDeviceOverride: (Boolean) -> Unit,
    onSelectDeviceShader: (ShaderPreset) -> Unit,
    onPreviewClick: () -> Unit,
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
                    .semantics { contentDescription = console.name }
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
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.size(24.dp),
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
                            .spFocusRing(shape = RoundedCornerShape(0.dp))
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

                    // Shader preview
                    if (previewUrl != null) {
                        Spacer(Modifier.height(SpSpacing.Small))
                        ShaderPreview(
                            imageUrl = previewUrl,
                            shader = effectiveShader,
                            onClick = onPreviewClick,
                            modifier = Modifier
                                .padding(horizontal = SpSpacing.Default)
                                .widthIn(max = 360.dp),
                        )
                        Spacer(Modifier.height(SpSpacing.Small))
                    }

                    SettingsDivider()

                    // Device override checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .spFocusRing(shape = RoundedCornerShape(0.dp))
                            .clickable { onToggleDeviceOverride(!hasDeviceOverride) }
                            .semantics { contentDescription = "Override on this device only" }
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
internal fun SettingsSectionHeader(title: String) {
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
            .spFocusRing(shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .focusable()
            .semantics {
                contentDescription = title
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
internal fun SettingsDivider() {
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
internal fun ShaderOption(
    shader: ShaderPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .spFocusRing(shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .focusable()
            .semantics {
                contentDescription = shader.displayName
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
