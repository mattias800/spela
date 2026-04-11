package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.DefaultKeyMappings
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.ShaderPreview
import com.spela.player.presentation.ui.components.ShaderPreviewDialog
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.gamepad.GamepadConfigScreen
import com.spela.player.presentation.ui.components.keymapping.KeyMappingScreen
import com.spela.player.presentation.ui.components.keymapping.platformKeyName
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.viewmodel.GamepadConfigIntent
import com.spela.player.presentation.viewmodel.GamepadConfigViewModel
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.feature.settings.SettingsDivider
import com.spela.player.presentation.ui.feature.settings.SettingsInfoRow
import com.spela.player.presentation.ui.feature.settings.SettingsSectionHeader
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsViewModel

@Composable
fun ConsoleSettingsScreen(
    consoleId: String,
    settingsViewModel: SettingsViewModel,
    keyMappingViewModel: KeyMappingViewModel,
    gamepadConfigViewModel: GamepadConfigViewModel? = null,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val settingsState by settingsViewModel.state.collectAsState()
    val keyMappingState by keyMappingViewModel.state.collectAsState()

    val console = settingsState.consoles.firstOrNull { it.id == consoleId }
    val consoleName = console?.name ?: "Console"
    val layout = remember(consoleId) { DefaultKeyMappings.getLayoutForConsole(consoleId) }

    LaunchedEffect(Unit) {
        settingsViewModel.onIntent(SettingsIntent.LoadSettings)
    }

    LaunchedEffect(consoleId) {
        keyMappingViewModel.onIntent(KeyMappingIntent.LoadMapping(consoleId))
    }

    val currentShader = settingsState.consoleShaders[consoleId]
        ?: settingsState.selectedShader
    val hasDeviceOverride = settingsState.deviceShaderOverrides.containsKey(consoleId)
    val deviceOverrideShader = settingsState.deviceShaderOverrides[consoleId]
    val effectiveShader = deviceOverrideShader ?: currentShader

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    SpScreen {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(
                    title = "$consoleName Settings",
                    showBack = true,
                    onBack = onBack,
                )
            }

            LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("console-settings-list"),
            contentPadding = PaddingValues(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Default,
            ),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            // Video Filter section
            item {
                SettingsSectionHeader(title = "Video Filter")
            }

            item {
                SpCard(onGradient = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SpSpacing.Small),
                    ) {
                        // Shader options
                        ShaderPreset.entries.forEachIndexed { index, shader ->
                            SpRadioOption(
                                title = shader.displayName,
                                description = shader.description,
                                isSelected = currentShader == shader,
                                onClick = {
                                    settingsViewModel.onIntent(
                                        SettingsIntent.SelectConsoleShader(consoleId, shader)
                                    )
                                },
                                modifier = if (index == 0) Modifier.autoFocus() else Modifier,
                            )
                            if (index < ShaderPreset.entries.size - 1) {
                                SettingsDivider()
                            }
                        }
                    }
                }
            }

            // Shader preview
            item {
                if (settingsState.serverUrl.isNotEmpty()) {
                    ShaderPreview(
                        imageUrl = "${settingsState.serverUrl}/api/consoles/${consoleId}/preview-screenshot",
                        shader = effectiveShader,
                        onClick = {
                            settingsViewModel.onIntent(
                                SettingsIntent.ShowShaderPreviewFullscreen(consoleId)
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = SpSpacing.Default)
                            .widthIn(max = 360.dp),
                    )
                }
            }

            // Device override
            item {
                SpCard(onGradient = true) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (hasDeviceOverride) {
                                        settingsViewModel.onIntent(
                                            SettingsIntent.SetDeviceOverride(consoleId, null)
                                        )
                                    } else {
                                        settingsViewModel.onIntent(
                                            SettingsIntent.SetDeviceOverride(consoleId, currentShader)
                                        )
                                    }
                                }
                                .gamepadFocusable(shape = RoundedCornerShape(SpSpacing.RadiusLarge))
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

                        if (hasDeviceOverride) {
                            SettingsDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SpColor.SurfaceVariant.copy(alpha = 0.5f))
                                    .padding(vertical = SpSpacing.Small),
                            ) {
                                Text(
                                    text = "Device Shader",
                                    style = SpTypography.LabelMedium,
                                    color = SpColor.OnBackgroundSecondary,
                                    modifier = Modifier.padding(
                                        start = SpSpacing.Default,
                                        bottom = SpSpacing.XSmall,
                                    ),
                                )
                                ShaderPreset.entries.forEachIndexed { index, shader ->
                                    SpRadioOption(
                                        title = shader.displayName,
                                        description = shader.description,
                                        isSelected = deviceOverrideShader == shader,
                                        onClick = {
                                            settingsViewModel.onIntent(
                                                SettingsIntent.SetDeviceOverride(consoleId, shader)
                                            )
                                        },
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

            // Controller Mapping section
            item {
                SettingsSectionHeader(title = "Controller Mapping")
            }

            // Gamepad config (connected controllers with activity indicators)
            if (gamepadConfigViewModel != null) {
                item {
                    val gamepadConfigState by gamepadConfigViewModel.state.collectAsState()
                    SpCard(onGradient = true) {
                        GamepadConfigScreen(
                            state = gamepadConfigState,
                            onConfigurePort = { port ->
                                keyMappingViewModel.onIntent(
                                    KeyMappingIntent.LoadMapping(consoleId, port)
                                )
                                gamepadConfigViewModel.onIntent(
                                    GamepadConfigIntent.SelectPortForMapping(port)
                                )
                            },
                            onSwapUp = { port ->
                                gamepadConfigViewModel.onIntent(
                                    GamepadConfigIntent.SwapPorts(port - 1, port)
                                )
                            },
                            onSwapDown = { port ->
                                gamepadConfigViewModel.onIntent(
                                    GamepadConfigIntent.SwapPorts(port, port + 1)
                                )
                            },
                        )
                    }
                }
            }

            item {
                val selectedPort = gamepadConfigViewModel?.state?.collectAsState()?.value?.selectedPort
                val portAssignment = gamepadConfigViewModel?.state?.collectAsState()?.value
                    ?.portAssignments?.find { it.port == selectedPort }
                val portLabel = if (selectedPort != null && portAssignment != null) {
                    "Player ${selectedPort + 1} \u2014 ${portAssignment.deviceName}"
                } else null

                SpCard(onGradient = true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(560.dp)
                            .padding(SpSpacing.Small)
                            .testTag("key-mapping-card"),
                    ) {
                        KeyMappingScreen(
                            layout = layout,
                            state = keyMappingState,
                            onButtonClick = { retroButtonId ->
                                keyMappingViewModel.onIntent(
                                    KeyMappingIntent.StartSingleButtonMap(retroButtonId)
                                )
                            },
                            onStartWizard = {
                                keyMappingViewModel.onIntent(
                                    KeyMappingIntent.StartWizard(consoleId)
                                )
                            },
                            onResetToDefaults = {
                                keyMappingViewModel.onIntent(KeyMappingIntent.ResetAll)
                            },
                            onCancelMapping = {
                                keyMappingViewModel.onIntent(KeyMappingIntent.CancelMapping)
                            },
                            onClearBinding = {
                                keyMappingViewModel.onIntent(KeyMappingIntent.ClearCurrentBinding)
                            },
                            keyNameResolver = ::platformKeyName,
                            portLabel = portLabel,
                            modifier = Modifier.padding(SpSpacing.ScreenHorizontal),
                        )
                    }
                }
            }

            // Console Info section
            item {
                SettingsSectionHeader(title = "Console Info")
            }

            item {
                SpCard(onGradient = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpSpacing.Default),
                    ) {
                        SettingsInfoRow(label = "Console", value = consoleName)
                        if (console?.defaultCore?.isNotEmpty() == true) {
                            Spacer(Modifier.height(SpSpacing.Small))
                            SettingsInfoRow(label = "Core", value = console.defaultCore)
                        }
                        if (console != null && console.gameCount > 0) {
                            Spacer(Modifier.height(SpSpacing.Small))
                            SettingsInfoRow(
                                label = "Games",
                                value = "${console.gameCount}",
                            )
                        }
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

    // Shader fullscreen preview dialog
    ShaderPreviewDialog(
        visible = settingsState.fullscreenPreviewConsoleId == consoleId,
        imageUrl = "${settingsState.serverUrl}/api/consoles/${consoleId}/preview-screenshot",
        shader = effectiveShader,
        onDismiss = { settingsViewModel.onIntent(SettingsIntent.DismissShaderPreviewFullscreen) },
    )
}

