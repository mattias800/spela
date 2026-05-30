package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.ShaderPreviewDialog
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.components.keymapping.PresetPickerDialog
import com.spela.player.presentation.ui.feature.library.darken
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text

private const val LIST_DETAIL_BREAKPOINT = 600

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    keyMappingViewModel: KeyMappingViewModel? = null,
    onBack: () -> Unit = {},
    onLogout: () -> Unit,
    onNavigateToConsoleSettings: (String) -> Unit = {},
    onNavigateToLicenses: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val keyMappingState = keyMappingViewModel?.state?.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(SettingsIntent.LoadSettings)
        keyMappingViewModel?.onIntent(KeyMappingIntent.LoadMapping("__default__"))
    }

    // Selected category state. Saveable so it survives forward+back navigation
    // (e.g. into a console's settings and back) instead of resetting to General.
    var selectedCategory by rememberSaveable(
        stateSaver = Saver(save = { it.name }, restore = { SettingsCategory.valueOf(it) }),
    ) { mutableStateOf(SettingsCategory.GENERAL) }
    // On phones, false means showing the category list. Also saveable so we
    // return to the detail (not the list) after navigating back.
    var showingDetail by rememberSaveable { mutableStateOf(false) }

    // Dialogs (shared across all categories)
    SettingsDialogs(
        state = state,
        viewModel = viewModel,
        keyMappingViewModel = keyMappingViewModel,
        keyMappingState = keyMappingState?.value,
        onLogout = onLogout,
    )

    val titleBarInset = LocalTitleBarInset.current
    val gradientColors = listOf(
        SpColor.PrimaryDark.darken(0.74f),
        SpColor.SecondaryDark.darken(0.82f),
    )

    SpScreen(gradientColors = gradientColors) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth > LIST_DETAIL_BREAKPOINT.dp

            if (isWide) {
                // List-detail: category list on left, content on right
                PlatformBackHandler { onBack() }
                Row(modifier = Modifier.fillMaxSize()) {
                    SettingsCategoryList(
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        username = state.username,
                        serverUrl = state.serverUrl,
                        modifier = Modifier.width(280.dp).fillMaxHeight(),
                        topPadding = titleBarInset,
                    )
                    SettingsCategoryContent(
                        category = selectedCategory,
                        state = state,
                        viewModel = viewModel,
                        syncState = syncState,
                        isOnline = isOnline,
                        connectionState = connectionState,
                        keyMappingViewModel = keyMappingViewModel,
                        keyMappingState = keyMappingState?.value,
                        onNavigateToConsoleSettings = onNavigateToConsoleSettings,
                        onNavigateToLicenses = onNavigateToLicenses,
                        onLogout = onLogout,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        topPadding = titleBarInset,
                    )
                }
            } else {
                // Phone: show category list or detail, not both
                if (showingDetail) {
                    PlatformBackHandler { showingDetail = false }
                    SettingsCategoryContent(
                        category = selectedCategory,
                        state = state,
                        viewModel = viewModel,
                        syncState = syncState,
                        isOnline = isOnline,
                        connectionState = connectionState,
                        keyMappingViewModel = keyMappingViewModel,
                        keyMappingState = keyMappingState?.value,
                        onNavigateToConsoleSettings = onNavigateToConsoleSettings,
                        onNavigateToLicenses = onNavigateToLicenses,
                        onLogout = onLogout,
                        modifier = Modifier.fillMaxSize(),
                        topPadding = titleBarInset,
                    )
                } else {
                    PlatformBackHandler { onBack() }
                    SettingsCategoryList(
                        selectedCategory = null,
                        onSelectCategory = {
                            selectedCategory = it
                            showingDetail = true
                        },
                        username = state.username,
                        serverUrl = state.serverUrl,
                        modifier = Modifier.fillMaxSize(),
                        topPadding = titleBarInset,
                    )
                }
            }
        }
    }
}

/**
 * All dialogs used by settings, extracted so they render regardless of
 * which category is visible.
 */
@Composable
private fun SettingsDialogs(
    state: com.spela.player.presentation.viewmodel.SettingsState,
    viewModel: SettingsViewModel,
    keyMappingViewModel: KeyMappingViewModel?,
    keyMappingState: com.spela.player.presentation.state.KeyMappingState?,
    onLogout: () -> Unit,
) {
    if (state.showLogoutConfirm) {
        SpConfirmDialog(
            title = "Sign Out",
            message = "Are you sure you want to sign out? You'll need to re-enter your credentials.",
            onDismiss = { viewModel.onIntent(SettingsIntent.DismissLogoutConfirm) },
            onConfirm = { viewModel.onIntent(SettingsIntent.Logout) },
            confirmText = "Sign Out",
            isDestructive = true,
        )
    }

    // Logout fires onLogout only after tokens have been cleared so the
    // post-logout navigation can't auto-login back into Home.
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLogout()
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

    // Preset picker dialog for controls section
    if (keyMappingState?.showPresetPicker == true && keyMappingViewModel != null) {
        PresetPickerDialog(
            presets = keyMappingState.availablePresets,
            activePresetId = keyMappingState.activePresetId,
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

internal enum class SecondScreenPageOption(
    val apiId: String,
    val displayName: String,
    val description: String,
) {
    ART("art", "Art Display", "Game artwork from SteamGridDB"),
    CONTROLS("controls", "Controls", "Touch gamepad controls"),
    DASHBOARD("dashboard", "Dashboard", "Stats and quick actions"),
    SAVE_SLOTS("save_slots", "Save Slots", "Visual save slot management"),
}
