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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.SaveState
import com.spela.player.presentation.intent.SessionDetailIntent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.social.formatRelativeTime
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SessionDetailViewModel
import com.spela.player.util.formatPlayTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    viewModel: SessionDetailViewModel,
    onBack: () -> Unit,
    onPlay: (gameId: String, sessionId: String) -> Unit,
    onDeleted: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.onIntent(SessionDetailIntent.LoadSession(sessionId))
    }

    // Navigate back after successful deletion
    LaunchedEffect(state.successMessage) {
        if (state.successMessage == "Session deleted") {
            onDeleted()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = state.session?.name ?: "Session",
                showBack = true,
                onBack = onBack,
            )

            if (state.isLoading && state.session == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading session...")
                }
            } else if (state.session != null) {
                val session = state.session ?: return
                val isRefreshing = state.isLoading || state.isLoadingSaves

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        viewModel.onIntent(SessionDetailIntent.LoadSession(sessionId))
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("session_detail_content"),
                        contentPadding = PaddingValues(vertical = SpSpacing.Default),
                    ) {
                        // Header
                        item {
                            SessionDetailHeader(
                                sessionName = session.name,
                                totalPlayTime = session.totalPlayTime,
                                lastPlayedAt = session.lastPlayedAt,
                                lastPlayedByUsername = session.lastPlayedByUsername,
                                onRename = { showRenameDialog = true },
                                onPlay = { onPlay(session.gameId, session.id) },
                            )
                            Spacer(Modifier.height(SpSpacing.XLarge))
                        }

                        // Save States section
                        item {
                            SpSectionHeader(
                                title = "Save States",
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            )
                            Spacer(Modifier.height(SpSpacing.Small))
                        }

                        if (state.saves.isEmpty() && !state.isLoadingSaves) {
                            item {
                                SpEmptyState(
                                    icon = Icons.Filled.Save,
                                    title = "No saves yet",
                                    message = "Play this session to create save states",
                                    modifier = Modifier.testTag("session_saves_empty"),
                                )
                            }
                        } else {
                            items(
                                state.saves,
                                key = { "save-${it.id}" },
                            ) { save ->
                                SessionSaveItem(
                                    save = save,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = SpSpacing.ScreenHorizontal,
                                            vertical = SpSpacing.XXSmall,
                                        ),
                                )
                            }
                        }

                        // Cheats section
                        item {
                            Spacer(Modifier.height(SpSpacing.XLarge))
                            SpSectionHeader(
                                title = "Cheats",
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            )
                            Spacer(Modifier.height(SpSpacing.Small))

                            SpInnerCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("session_cheats_section"),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SpSpacing.Default),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Enable Cheats",
                                        style = SpTypography.BodyMedium,
                                        color = SpColor.OnCard,
                                    )
                                    Switch(
                                        checked = state.cheatsEnabled,
                                        onCheckedChange = { enabled ->
                                            viewModel.onIntent(
                                                SessionDetailIntent.ToggleCheatsEnabled(sessionId, enabled)
                                            )
                                        },
                                        modifier = Modifier
                                            .testTag("session_cheats_toggle")
                                            .semantics { contentDescription = "Toggle cheats" },
                                    )
                                }
                                if (state.cheatsEnabled && state.enabledCheatIndices.isNotEmpty()) {
                                    Text(
                                        text = "${state.enabledCheatIndices.size} cheat(s) active",
                                        style = SpTypography.LabelSmall,
                                        color = SpColor.OnBackgroundTertiary,
                                        modifier = Modifier
                                            .padding(
                                                start = SpSpacing.Default,
                                                bottom = SpSpacing.Small,
                                            )
                                            .testTag("session_cheats_count"),
                                    )
                                }
                            }
                        }

                        // Danger Zone section
                        item {
                            Spacer(Modifier.height(SpSpacing.XXLarge))
                            SpSectionHeader(
                                title = "Danger Zone",
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            )
                            Spacer(Modifier.height(SpSpacing.Small))

                            SpButton(
                                text = "Delete Session",
                                onClick = { viewModel.onIntent(SessionDetailIntent.ShowDeleteConfirm) },
                                style = SpButtonStyle.Outlined,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .testTag("session_delete_button"),
                            )
                            Spacer(Modifier.height(SpSpacing.XXLarge))
                        }
                    }
                }
            }
        }

        // Rename dialog
        if (showRenameDialog) {
            val session = state.session
            var newName by remember { mutableStateOf(session?.name ?: "") }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Session") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Session Name") },
                        singleLine = true,
                        modifier = Modifier.testTag("session_detail_rename_input"),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.onIntent(
                                    SessionDetailIntent.RenameSession(sessionId, newName.trim())
                                )
                                showRenameDialog = false
                            }
                        },
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Delete confirmation dialog
        if (state.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = {
                    viewModel.onIntent(SessionDetailIntent.DismissDeleteConfirm)
                },
                title = { Text("Delete Session") },
                text = {
                    Text(
                        "Delete \"${state.session?.name}\"? All saves in this session will be permanently removed.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.onIntent(SessionDetailIntent.DeleteSession(sessionId))
                        },
                    ) {
                        Text("Delete", color = SpColor.Error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.onIntent(SessionDetailIntent.DismissDeleteConfirm)
                        },
                    ) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("session_delete_dialog"),
            )
        }

        // Snackbars
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(SessionDetailIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(SessionDetailIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SpSnackbar(
            data = state.successMessage?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Success,
                )
            },
            onDismiss = { viewModel.onIntent(SessionDetailIntent.DismissSuccess) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SessionDetailHeader(
    sessionName: String,
    totalPlayTime: Long,
    lastPlayedAt: String?,
    lastPlayedByUsername: String?,
    onRename: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal)
            .testTag("session_detail_header"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sessionName,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnBackground,
                    modifier = Modifier.semantics {
                        contentDescription = "Session: $sessionName"
                    },
                )

                Spacer(Modifier.height(SpSpacing.XSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    if (totalPlayTime > 0) {
                        Text(
                            text = formatPlayTime(totalPlayTime),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                    if (lastPlayedAt != null) {
                        Text(
                            text = formatRelativeTime(lastPlayedAt),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                    if (lastPlayedByUsername != null) {
                        Text(
                            text = "Last played by $lastPlayedByUsername",
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }

            Row {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.testTag("session_detail_rename_button"),
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Rename session",
                        tint = SpColor.OnBackgroundSecondary,
                    )
                }
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.testTag("session_detail_play_button"),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play session",
                        tint = SpColor.Accent,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSaveItem(
    save: SaveState,
    modifier: Modifier = Modifier,
) {
    SpInnerCard(
        modifier = modifier.testTag("session_save_item_${save.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = save.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    if (save.createdAt != null) {
                        Text(
                            text = formatRelativeTime(save.createdAt.toString()),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                    if (save.fileSize > 0) {
                        Text(
                            text = formatFileSize(save.fileSize),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                    if (save.isAuto) {
                        Text(
                            text = "Auto",
                            style = SpTypography.LabelSmall,
                            color = SpColor.Primary,
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }
}
