package com.spela.player.presentation.ui.screen

import com.spela.player.util.formatBytes
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.SaveState
import com.spela.player.presentation.intent.SessionDetailIntent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpHeroCover
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.feature.library.darken
import com.spela.player.presentation.ui.feature.library.getConsoleGradient
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.feature.sessiondetail.SessionCheatsSection
import com.spela.player.presentation.ui.feature.sessiondetail.SessionCoreLockChip
import com.spela.player.presentation.ui.components.SpPlayInfo
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
    onNavigateToSession: ((String) -> Unit)? = null,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    /** Tracks which per-save row's `…` menu is open; null when none. */
    var cloneFromSaveId by remember { mutableStateOf<String?>(null) }

    // After a successful clone, navigate to the new session's detail.
    // Fall back to a simple "navigate back and up" behavior when the
    // caller didn't provide an onNavigateToSession hook (host app bug).
    LaunchedEffect(state.clonedSessionId) {
        val newId = state.clonedSessionId
        if (newId != null) {
            onNavigateToSession?.invoke(newId)
            viewModel.onIntent(SessionDetailIntent.ClearCloneNavigation)
        }
    }

    LaunchedEffect(sessionId) {
        viewModel.onIntent(SessionDetailIntent.LoadSession(sessionId))
    }

    // Navigate back after successful deletion
    LaunchedEffect(state.successMessage) {
        if (state.successMessage == "Session deleted") {
            onDeleted()
        }
    }

    // Per-console gradient background (matching GameDetailScreen)
    val backgroundColors = remember(state.game?.consoleId) {
        val consoleId = state.game?.consoleId
        if (consoleId != null) {
            val (from, to) = getConsoleGradient(consoleId, null)
            listOf(from.darken(0.65f), to.darken(0.65f))
        } else {
            listOf(SpColor.Background, SpColor.Background)
        }
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD
    val focusMemory = rememberFocusMemoryState()

    SpScreen(gradientColors = backgroundColors) {
        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(
                    title = state.session?.name ?: "Session",
                    showBack = true,
                    onBack = onBack,
                    onGradient = true,
                )
            }

            if (state.isLoading && state.session == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ScreenLoadingIndicator(message = "Loading session...")
                }
            } else if (state.session != null) {
                val session = state.session!!
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
                        // Hero screenshot
                        if (session.screenshotUrl != null) {
                            item {
                                SpHeroCover(
                                    imageUrl = session.screenshotUrl,
                                    contentDescription = "Session screenshot",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        // Header: game info + session info + actions
                        item {
                            SessionDetailHeader(
                                sessionName = session.name,
                                totalPlayTime = session.totalPlayTime,
                                lastPlayedAt = session.lastPlayedAt,
                                lastPlayedByUsername = session.lastPlayedByUsername,
                                cheatsEnabled = state.cheatsEnabled,
                                memberCount = session.memberCount,
                                game = state.game,
                                pinnedCoreSha256 = session.pinnedCoreSha256,
                                userLockedCoreVersion = session.userLockedCoreVersion,
                                onRename = { showRenameDialog = true },
                                onPlay = { onPlay(session.gameId, session.id) },
                                onClone = { showCloneDialog = true },
                                onUnlockCoreVersion = {
                                    viewModel.onIntent(
                                        SessionDetailIntent.UnlockCoreVersion(sessionId),
                                    )
                                },
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
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
                            itemsIndexed(
                                state.saves,
                                key = { _, s -> "save-${s.id}" },
                            ) { _, save ->
                                SessionSaveItem(
                                    save = save,
                                    onCloneFromSave = { cloneFromSaveId = save.id },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = SpSpacing.ScreenHorizontal,
                                            vertical = SpSpacing.XSmall,
                                        )
                                        .focusRestoreItem(
                                            key = "session_save_${save.id}",
                                        ),
                                )
                            }
                        }

                        // Cheats section
                        item {
                            Spacer(Modifier.height(SpSpacing.XLarge))
                            SessionCheatsSection(
                                cheatsEnabled = state.cheatsEnabled,
                                enabledCheatIndices = state.enabledCheatIndices,
                                availableCheats = state.availableCheats,
                                isLoadingCheats = state.isLoadingCheats,
                                cheatsLoadAttempted = state.cheatsLoadAttempted,
                                onToggleCheatsEnabled = { enabled ->
                                    viewModel.onIntent(
                                        SessionDetailIntent.ToggleCheatsEnabled(sessionId, enabled)
                                    )
                                },
                                onToggleCheatAtIndex = { index ->
                                    viewModel.onIntent(
                                        SessionDetailIntent.ToggleCheatAtIndex(sessionId, index)
                                    )
                                },
                                onSelectAll = {
                                    viewModel.onIntent(
                                        SessionDetailIntent.SelectAllCheats(sessionId)
                                    )
                                },
                                onDeselectAll = {
                                    viewModel.onIntent(
                                        SessionDetailIntent.DeselectAllCheats(sessionId)
                                    )
                                },
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            )
                        }

                        // Delete section
                        item {
                            Spacer(Modifier.height(SpSpacing.XXLarge))

                            SpCard(
                                onGradient = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SpSpacing.ScreenHorizontal),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SpSpacing.Default),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = SpColor.Error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(SpSpacing.Medium))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Delete this session and all its save data permanently.",
                                            style = SpTypography.BodySmall,
                                            color = SpColor.OnBackgroundTertiary,
                                        )
                                    }
                                    Spacer(Modifier.width(SpSpacing.Medium))
                                    SpSecondaryButton(
                                        text = "Delete Session",
                                        onClick = { viewModel.onIntent(SessionDetailIntent.ShowDeleteConfirm) },
                                        modifier = Modifier.testTag("session_delete_button"),
                                    )
                                }
                            }
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

        // Clone session dialog — US-2 (clone own session) + US-1
        // (non-owners clone a shared session to their library use the
        // SharedSessionDetail screen instead).
        if (showCloneDialog) {
            val source = state.session
            var cloneName by remember(source?.id) { mutableStateOf((source?.name ?: "") + " (Copy)") }
            AlertDialog(
                onDismissRequest = { showCloneDialog = false },
                title = { Text("Clone Session") },
                text = {
                    OutlinedTextField(
                        value = cloneName,
                        onValueChange = { cloneName = it },
                        label = { Text("New Session Name") },
                        singleLine = true,
                        modifier = Modifier.testTag("session_detail_clone_input"),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (cloneName.isNotBlank()) {
                                viewModel.onIntent(
                                    SessionDetailIntent.CloneSession(sessionId, cloneName.trim(), null)
                                )
                                showCloneDialog = false
                            }
                        },
                        modifier = Modifier.testTag("session_detail_clone_confirm"),
                    ) {
                        Text("Clone")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCloneDialog = false }) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("session_detail_clone_dialog"),
            )
        }

        // Clone-from-specific-save dialog — US-3. [cloneFromSaveId] is
        // a String because SaveState.id is a String; backend clone API
        // takes Long so we parse at dispatch time.
        cloneFromSaveId?.let { saveIdStr ->
            val source = state.session
            var cloneName by remember(source?.id, saveIdStr) {
                mutableStateOf((source?.name ?: "") + " (Copy)")
            }
            AlertDialog(
                onDismissRequest = { cloneFromSaveId = null },
                title = { Text("Clone from this save") },
                text = {
                    OutlinedTextField(
                        value = cloneName,
                        onValueChange = { cloneName = it },
                        label = { Text("New Session Name") },
                        singleLine = true,
                        modifier = Modifier.testTag("session_detail_clone_from_save_input"),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (cloneName.isNotBlank()) {
                                val parsed = saveIdStr.toLongOrNull()
                                viewModel.onIntent(
                                    SessionDetailIntent.CloneSession(sessionId, cloneName.trim(), parsed)
                                )
                                cloneFromSaveId = null
                            }
                        },
                        modifier = Modifier.testTag("session_detail_clone_from_save_confirm"),
                    ) {
                        Text("Clone")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { cloneFromSaveId = null }) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("session_detail_clone_from_save_dialog"),
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
        } // CompositionLocalProvider
    }
}

@Composable
private fun SessionDetailHeader(
    sessionName: String,
    totalPlayTime: Long,
    lastPlayedAt: String?,
    lastPlayedByUsername: String?,
    cheatsEnabled: Boolean,
    memberCount: Int,
    game: Game?,
    pinnedCoreSha256: String?,
    userLockedCoreVersion: Boolean,
    onRename: () -> Unit,
    onPlay: () -> Unit,
    onClone: () -> Unit,
    onUnlockCoreVersion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("session_detail_header")
            .semantics { contentDescription = "Session: $sessionName" },
    ) {
        // Game info row — matches RelayHeader pattern (80x107 cover + game title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpCoverArt(
                imageUrl = game?.coverUrl,
                contentDescription = game?.let { "${it.title} cover" } ?: "Game cover",
                modifier = Modifier.size(width = 80.dp, height = 107.dp),
                cornerRadius = SpSpacing.RadiusLarge,
                aspectRatio = null,
            )
            Spacer(Modifier.width(SpSpacing.Default))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game?.title ?: "",
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game != null && game.consoleName.isNotEmpty()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = game.consoleName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
                Spacer(Modifier.height(SpSpacing.Small))
                Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                    if (totalPlayTime > 0) {
                        SpChip(text = formatPlayTime(totalPlayTime))
                    }
                    if (cheatsEnabled) {
                        SpChip(text = "Cheats", color = SpColor.Warning)
                    }
                    if (memberCount > 1) {
                        SpChip(text = "$memberCount players")
                    }
                }
            }
        }

        // Locked-to-version chip (#672). Rendered whenever the user has
        // explicitly locked the session — even if the sha is missing,
        // because otherwise the user can't see or clear an invisible lock
        // (`userLockedCoreVersion = true` without a pin should be rare,
        // but the chip must still appear so the "Use the latest version
        // instead" escape hatch remains reachable). The chip itself
        // handles the empty-sha render path gracefully.
        if (userLockedCoreVersion) {
            Spacer(Modifier.height(SpSpacing.Default))
            SessionCoreLockChip(
                pinnedCoreSha256 = pinnedCoreSha256.orEmpty(),
                onUnlock = onUnlockCoreVersion,
            )
        }

        // Last played info
        Spacer(Modifier.height(SpSpacing.Default))
        SpPlayInfo(
            totalPlayTime = totalPlayTime,
            lastPlayedAt = lastPlayedAt,
            lastPlayedByUsername = lastPlayedByUsername,
        )

        Spacer(Modifier.height(SpSpacing.Default))

        // Action row — rename + play (matches shared session's button pattern)
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpButton(
                text = "Play",
                onClick = onPlay,
                modifier = Modifier
                    .focusRestoreItem(
                        key = "session_detail_play",
                        isDefault = true,
                    )
                    .testTag("session_detail_play_button"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            SpSecondaryButton(
                text = "Edit",
                onClick = onRename,
                modifier = Modifier.testTag("session_detail_rename_button"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Rename session",
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
            // Overflow menu — clone lives here per PO brief (secondary action).
            Box {
                SpIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "More session actions",
                    onClick = { showMoreMenu = true },
                    modifier = Modifier.testTag("session_detail_more_menu"),
                )
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Clone session", style = SpTypography.BodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        },
                        onClick = {
                            showMoreMenu = false
                            onClone()
                        },
                        modifier = Modifier.testTag("session_detail_clone_menu_item"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSaveItem(
    save: SaveState,
    onCloneFromSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveMenu by remember { mutableStateOf(false) }
    SpCard(
        onGradient = true,
        modifier = modifier.testTag("session_save_item_${save.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                tint = SpColor.Primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = save.name,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = buildString {
                        if (save.createdAt != null) {
                            append(formatRelativeTime(save.createdAt.toString()))
                        }
                        if (save.fileSize > 0) {
                            if (isNotEmpty()) append(" \u00b7 ")
                            append(formatBytes(save.fileSize))
                        }
                    },
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
            if (save.isAuto) {
                SpChip(text = "Auto", color = SpColor.Primary)
            }
            // Per-save `…` menu — holds "Clone from this save" (US-3).
            // Clone is the ONLY entry today; future per-save actions
            // (rename / delete / download) will join this menu.
            Box {
                SpIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "Save actions",
                    onClick = { showSaveMenu = true },
                    modifier = Modifier.testTag("session_save_menu_${save.id}"),
                )
                DropdownMenu(
                    expanded = showSaveMenu,
                    onDismissRequest = { showSaveMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Clone from this save", style = SpTypography.BodyMedium) },
                        leadingIcon = {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        },
                        onClick = {
                            showSaveMenu = false
                            onCloneFromSave()
                        },
                        modifier = Modifier.testTag("session_save_clone_item_${save.id}"),
                    )
                }
            }
        }
    }
}