package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.spela.player.domain.model.GameSession
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpIconButtonVariant
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpStatusChip
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpPlayInfo
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun SessionsSection(
    sessions: List<GameSession>,
    isLoading: Boolean,
    onContinueSession: (GameSession) -> Unit,
    onContinueSessionFromTitleScreen: ((GameSession) -> Unit)? = null,
    onCreateSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    /**
     * Clone entry point. Per #553 PO guidance clone is a SECONDARY
     * action and only appears inside the per-session `…` overflow
     * menu. [name] lets the confirmation dialog pass an edited name
     * (null → server defaults to `"{source} (Copy)"`). [saveId] is
     * always null here — per-save cloning (US-3) lives on the
     * Session Detail screen's save rows, not in this list.
     */
    onCloneSession: (sessionId: String, name: String?, saveId: Long?) -> Unit,
    /**
     * #885 — opens the share-session dialog seeded from this session.
     * The handler is wired by GameDetailScreen to dispatch
     * [GameDetailIntent.ShowShareSessionDialog] only when
     * [shareEnabled] is true; if the host doesn't pass a callback,
     * the menu item is hidden entirely.
     */
    onShareSession: ((GameSession) -> Unit)? = null,
    /**
     * #885 — capability gate. True when save-state-based sharing
     * makes sense for this game (console supports save states AND
     * the user hasn't opted out per #804). False on ScummVM, demo
     * cores, or user-disabled consoles. When false the share menu
     * item is hidden entirely — no disabled+tooltip noise.
     */
    shareEnabled: Boolean = false,
    onSessionSelected: ((GameSession) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by remember { mutableStateOf<GameSession?>(null) }
    var showDeleteDialog by remember { mutableStateOf<GameSession?>(null) }
    var showCloneDialog by remember { mutableStateOf<GameSession?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    SpTitledSection(
        title = "Sessions",
        icon = Icons.Outlined.Gamepad,
        modifier = modifier.testTag("sessions_section"),
        titleTrailing = {
            SpSecondaryButton(
                text = "New Session",
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("create_session_button"),
            )
        },
    ) {
        if (sessions.isEmpty() && !isLoading) {
            Text(
                text = "No sessions yet. Press Play to start your first playthrough.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.testTag("sessions_empty"),
            )
        }

        sessions.forEachIndexed { index, session ->
            SessionItem(
                session = session,
                isCurrent = index == 0,
                onClick = {
                    if (onSessionSelected != null) {
                        onSessionSelected(session)
                    } else {
                        onContinueSession(session)
                    }
                },
                onContinue = { onContinueSession(session) },
                onContinueFromTitleScreen = onContinueSessionFromTitleScreen?.let { callback -> { callback(session) } },
                onRename = { showRenameDialog = session },
                onDelete = { showDeleteDialog = session },
                onClone = { showCloneDialog = session },
                onShare = if (shareEnabled && onShareSession != null) {
                    { onShareSession.invoke(session) }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.XXSmall)
                    // Capture this row in the screen's LocalFocusMemory
                    // so back-nav from the SessionDetail screen restores
                    // focus to the same session the user opened, rather
                    // than falling back to the Play/Download button's
                    // `isDefault = true`. Key is the session id so it
                    // survives the list reordering on lastPlayedAt
                    // updates.
                    .focusRestoreItem(key = "game_detail_session_${session.id}"),
            )
        }
    }

    // Rename dialog
    showRenameDialog?.let { session ->
        var newName by remember { mutableStateOf(session.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Session") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Session Name") },
                    singleLine = true,
                    modifier = Modifier.testTag("rename_session_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenameSession(session.id, newName.trim())
                            showRenameDialog = null
                        }
                    },
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Delete dialog
    showDeleteDialog?.let { session ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Session") },
            text = { Text("Delete \"${session.name}\"? All saves in this session will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSession(session.id)
                        showDeleteDialog = null
                    },
                ) {
                    Text("Delete", color = SpColor.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Clone dialog — mirrors the rename dialog pattern. Pre-fills
    // "{source.name} (Copy)" but lets the user edit before confirming.
    showCloneDialog?.let { session ->
        var cloneName by remember(session.id) { mutableStateOf("${session.name} (Copy)") }
        AlertDialog(
            onDismissRequest = { showCloneDialog = null },
            title = { Text("Clone Session") },
            text = {
                OutlinedTextField(
                    value = cloneName,
                    onValueChange = { cloneName = it },
                    label = { Text("New Session Name") },
                    singleLine = true,
                    modifier = Modifier.testTag("clone_session_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (cloneName.isNotBlank()) {
                            onCloneSession(session.id, cloneName.trim(), null)
                            showCloneDialog = null
                        }
                    },
                    modifier = Modifier.testTag("clone_session_confirm"),
                ) {
                    Text("Clone")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloneDialog = null }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("clone_session_dialog"),
        )
    }

    // Create dialog
    if (showCreateDialog) {
        var sessionName by remember { mutableStateOf("Playthrough ${sessions.size + 1}") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Session") },
            text = {
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    label = { Text("Session Name") },
                    singleLine = true,
                    modifier = Modifier.testTag("create_session_input"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (sessionName.isNotBlank()) {
                            onCreateSession(sessionName.trim())
                            showCreateDialog = false
                        }
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SessionItem(
    session: GameSession,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onContinue: () -> Unit,
    onContinueFromTitleScreen: (() -> Unit)?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onClone: () -> Unit,
    /**
     * #885 — null when sharing isn't available for this row (capability
     * gated upstream). When non-null, an extra "Share session…" menu
     * item appears between Clone and Rename and invokes this on click.
     */
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isMultiplayer = session.memberCount > 1

    SpInnerCard(
        onClick = onClick,
        modifier = modifier
            .testTag("session_item_${session.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    Text(
                        text = session.name,
                        style = SpTypography.TitleSmall,
                        color = SpColor.OnCard,
                        maxLines = 1,
                        modifier = Modifier.semantics {
                            contentDescription = "Session: ${session.name}"
                        },
                    )
                    if (isCurrent) {
                        SpStatusChip(
                            text = "Current",
                            color = SpColor.Success,
                            modifier = Modifier.testTag("session_current_badge"),
                        )
                    }
                    if (isMultiplayer || session.isSharedSession) {
                        SpChip(
                            text = if (session.isSharedSession) "Shared Session" else "Multiplayer",
                            color = SpColor.Primary,
                            isSelected = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.People,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = SpColor.Primary,
                                )
                            },
                            modifier = Modifier.testTag("session_multiplayer_badge_${session.id}"),
                        )
                    }
                }

                if (session.isSharedSession && session.memberUsernames.isNotEmpty()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = session.memberUsernames.joinToString(", "),
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        modifier = Modifier.testTag("session_member_names_${session.id}"),
                    )
                } else if (isMultiplayer && session.memberAvatars.isNotEmpty()) {
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    MemberAvatarsRow(
                        avatars = session.memberAvatars,
                        memberCount = session.memberCount,
                        modifier = Modifier.testTag("session_member_avatars_${session.id}"),
                    )
                }

                Spacer(Modifier.height(SpSpacing.XXSmall))
                SpPlayInfo(
                    totalPlayTime = session.totalPlayTime,
                    lastPlayedAt = session.lastPlayedAt,
                    lastPlayedByUsername = session.lastPlayedByUsername,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Session-level actions go into a single `…` overflow menu.
                // Rename/Delete/Clone are all secondary — only Play is
                // surfaced as a primary button in this row. Clone in
                // particular is required by #553 PO brief to live inside
                // a menu, never as its own CTA.
                Box {
                    var showActionsMenu by remember { mutableStateOf(false) }
                    // Transparent variant so the kebab reads as just an icon
                    // on the row background, not a chip-on-a-chip — matches
                    // the visual treatment of the Play button next to it.
                    // See #854.
                    SpIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = "Session actions",
                        onClick = { showActionsMenu = true },
                        variant = SpIconButtonVariant.Transparent,
                        modifier = Modifier.testTag("session_actions_menu_${session.id}"),
                    )
                    DropdownMenu(
                        expanded = showActionsMenu,
                        onDismissRequest = { showActionsMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clone session", style = SpTypography.BodyMedium) },
                            leadingIcon = {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            },
                            onClick = {
                                showActionsMenu = false
                                onClone()
                            },
                            modifier = Modifier.testTag("session_action_clone_${session.id}"),
                        )
                        // #885 — Share session… below Clone, between
                        // CRUD-on-the-row items and Rename. Only
                        // present when the host wired a callback
                        // (capability-gated upstream on PlaySemantics
                        // .ResumesFromSaveState — i.e. there's a
                        // save to share).
                        if (onShare != null) {
                            DropdownMenuItem(
                                text = { Text("Share session…", style = SpTypography.BodyMedium) },
                                leadingIcon = {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                                },
                                onClick = {
                                    showActionsMenu = false
                                    onShare()
                                },
                                modifier = Modifier.testTag("session_action_share_${session.id}"),
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename", style = SpTypography.BodyMedium) },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            },
                            onClick = {
                                showActionsMenu = false
                                onRename()
                            },
                            modifier = Modifier.testTag("session_action_rename_${session.id}"),
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", style = SpTypography.BodyMedium, color = SpColor.Error) },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = SpColor.Error)
                            },
                            onClick = {
                                showActionsMenu = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("session_action_delete_${session.id}"),
                        )
                    }
                }
                // Play button with optional dropdown for "Continue from Title Screen"
                val showChevron = session.lastPlayedAt != null && onContinueFromTitleScreen != null
                Box {
                    var showPlayMenu by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onContinue) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Continue session",
                                tint = SpColor.Accent,
                            )
                        }
                        if (showChevron) {
                            IconButton(
                                onClick = { showPlayMenu = true },
                                modifier = Modifier.size(SpSpacing.IconLarge),
                            ) {
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = "More play options",
                                    tint = SpColor.Accent,
                                    modifier = Modifier.size(SpSpacing.IconDefault),
                                )
                            }
                        }
                    }
                    if (showChevron) {
                        DropdownMenu(
                            expanded = showPlayMenu,
                            onDismissRequest = { showPlayMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Continue from Title Screen",
                                        style = SpTypography.BodyMedium,
                                    )
                                },
                                onClick = {
                                    showPlayMenu = false
                                    onContinueFromTitleScreen()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberAvatarsRow(
    avatars: List<String>,
    memberCount: Int,
    modifier: Modifier = Modifier,
) {
    val maxVisible = 4
    val visibleAvatars = avatars.take(maxVisible)
    val overflow = memberCount - visibleAvatars.size

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            visibleAvatars.forEachIndexed { index, avatarUrl ->
                SpAvatar(
                    username = "",
                    avatarUrl = avatarUrl,
                    size = 24.dp,
                    modifier = Modifier.offset(x = (index * 18).dp),
                )
            }
        }
        Spacer(Modifier.width(((visibleAvatars.size - 1) * 18 + 24).dp))
        if (overflow > 0) {
            Spacer(Modifier.width(SpSpacing.XSmall))
            Text(
                text = "+$overflow",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.testTag("session_avatar_overflow"),
            )
        }
    }
}
