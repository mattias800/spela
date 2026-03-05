package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.spela.player.domain.model.GameSession
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.social.formatRelativeTime
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatPlayTime

@Composable
internal fun SessionsSection(
    sessions: List<GameSession>,
    isLoading: Boolean,
    onContinueSession: (GameSession) -> Unit,
    onCreateSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onDeleteSession: (String) -> Unit,
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
                text = "No sessions yet. Start a new playthrough to track your progress.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.testTag("sessions_empty"),
            )
            Spacer(Modifier.height(SpSpacing.Default))
        }

        sessions.forEach { session ->
            SessionItem(
                session = session,
                onContinue = { onContinueSession(session) },
                onRename = { showRenameDialog = session },
                onDelete = { showDeleteDialog = session },
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
    onContinue: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpInnerCard(
        modifier = modifier
            .clickable(onClick = onContinue)
            .testTag("session_item_${session.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    modifier = Modifier.semantics {
                        contentDescription = "Session: ${session.name}"
                    },
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    if (session.totalPlayTime > 0) {
                        Text(
                            text = formatPlayTime(session.totalPlayTime),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                    if (session.lastPlayedAt != null) {
                        Text(
                            text = formatRelativeTime(session.lastPlayedAt),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                    if (session.lastPlayedByUsername != null) {
                        Text(
                            text = session.lastPlayedByUsername,
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }

            Row {
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
