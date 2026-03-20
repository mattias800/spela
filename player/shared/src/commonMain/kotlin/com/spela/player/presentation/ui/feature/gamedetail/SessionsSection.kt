package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material3.AlertDialog
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
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpPlayInfo
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun SessionsSection(
    sessions: List<GameSession>,
    isLoading: Boolean,
    onContinueSession: (GameSession) -> Unit,
    onCreateSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDuplicateSession: (String) -> Unit,
    onSessionSelected: ((GameSession) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showRenameDialog by remember { mutableStateOf<GameSession?>(null) }
    var showDeleteDialog by remember { mutableStateOf<GameSession?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    SpTitledSection(
        title = "Sessions",
        icon = Icons.Outlined.Gamepad,
        modifier = modifier.testTag("sessions_section"),
        titleTrailing = if (sessions.isNotEmpty()) {
            {
                Text(
                    text = "(${sessions.size})",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        } else null,
    ) {
        if (sessions.isEmpty() && !isLoading) {
            Text(
                text = "No sessions yet. Press Play to start your first playthrough.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.testTag("sessions_empty"),
            )
            Spacer(Modifier.height(SpSpacing.Default))
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
                onRename = { showRenameDialog = session },
                onDelete = { showDeleteDialog = session },
                onDuplicate = { onDuplicateSession(session.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.XXSmall),
            )
        }

        Spacer(Modifier.height(SpSpacing.Small))
        SpButton(
            text = "Start New Session",
            onClick = { showCreateDialog = true },
            style = SpButtonStyle.Outlined,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_session_button"),
        )
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
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMultiplayer = session.memberCount > 1

    SpInnerCard(
        modifier = modifier
            .clickable(onClick = onClick)
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
                        SpChip(
                            text = "Current",
                            color = SpColor.Primary,
                            isSelected = true,
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

            Row {
                IconButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Duplicate session",
                        tint = SpColor.OnBackgroundSecondary,
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Rename session",
                        tint = SpColor.OnBackgroundSecondary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete session",
                        tint = SpColor.OnBackgroundSecondary,
                    )
                }
                IconButton(onClick = onContinue) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Continue session",
                        tint = SpColor.Accent,
                    )
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
