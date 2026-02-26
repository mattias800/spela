package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.presentation.ui.components.ScreenshotLightbox
import com.spela.player.presentation.ui.components.social.formatRelativeTime
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.social.SharedSaveItem
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun ScreenshotsSection(screenshots: List<String>) {
    if (screenshots.isEmpty()) return

    var lightboxIndex by remember { mutableStateOf<Int?>(null) }

    Spacer(Modifier.height(SpSpacing.XXLarge))
    Text(
        text = "Screenshots",
        style = SpTypography.HeadlineSmall,
        color = SpColor.OnBackground,
        modifier = Modifier
            .padding(horizontal = SpSpacing.ScreenHorizontal)
            .semantics { heading() },
    )
    Spacer(Modifier.height(SpSpacing.Medium))
    LazyRow(
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(screenshots.size, key = { screenshots[it] }) { index ->
            SpInnerCard(
                modifier = Modifier.width(280.dp),
                onClick = { lightboxIndex = index },
            ) {
                AsyncImage(
                    model = screenshots[index],
                    contentDescription = "Game screenshot",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
    ScreenshotLightbox(
        visible = lightboxIndex != null,
        screenshotUrls = screenshots,
        initialIndex = lightboxIndex ?: 0,
        onDismiss = { lightboxIndex = null },
    )
}

@Composable
fun SaveStatesSection(
    saveStates: List<SaveState>,
    unsyncedCount: Int = 0,
    isSelectionMode: Boolean = false,
    selectedSaveIds: Set<Long> = emptySet(),
    onDelete: ((Long) -> Unit)? = null,
    onRename: ((Long, String) -> Unit)? = null,
    onUpdateNotes: ((Long, String) -> Unit)? = null,
    onToggleSelection: ((Long) -> Unit)? = null,
    onToggleSelectionMode: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onDeleteSelected: (() -> Unit)? = null,
    onExport: ((Long) -> Unit)? = null,
) {
    SpTitledSection(
        title = "Save States",
        icon = Icons.Outlined.Save,
        titleTrailing = {
            if (isSelectionMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                    IconButton(onClick = { onSelectAll?.invoke() }) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "Select all", tint = SpColor.OnBackgroundSecondary)
                    }
                    SpButton(
                        text = "Delete (${selectedSaveIds.size})",
                        onClick = { onDeleteSelected?.invoke() },
                        style = SpButtonStyle.Outlined,
                        enabled = selectedSaveIds.isNotEmpty(),
                    )
                    SpButton(
                        text = "Cancel",
                        onClick = { onToggleSelectionMode?.invoke() },
                        style = SpButtonStyle.Ghost,
                    )
                }
            } else if (saveStates.size > 1 && onToggleSelectionMode != null) {
                SpButton(
                    text = "Select",
                    onClick = { onToggleSelectionMode() },
                    style = SpButtonStyle.Ghost,
                )
            }
        },
    ) {
        // Sync status summary
        if (unsyncedCount > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                modifier = Modifier.padding(bottom = SpSpacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = SpColor.Warning,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "$unsyncedCount unsynced save${if (unsyncedCount > 1) "s" else ""}",
                    style = SpTypography.LabelSmall,
                    color = SpColor.Warning,
                )
            }
        }

        if (saveStates.isEmpty()) {
            SpEmptyStates.NoSaveStates(modifier = Modifier.fillMaxWidth())
        } else {
            saveStates.forEach { save ->
                SaveStateItem(
                    saveState = save,
                    isSelectionMode = isSelectionMode,
                    isSelected = save.id in selectedSaveIds,
                    onToggleSelection = onToggleSelection?.let { { it(save.id) } },
                    onDelete = onDelete?.let { { it(save.id) } },
                    onRename = onRename?.let { { name -> it(save.id, name) } },
                    onUpdateNotes = onUpdateNotes?.let { { notes -> it(save.id, notes) } },
                    onExport = onExport?.let { { it(save.id) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.XSmall),
                )
            }
        }
    }
}

@Composable
fun InfoColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
        )
        Spacer(Modifier.height(SpSpacing.XXSmall))
        Text(
            text = value,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackground,
        )
    }
}

@Composable
fun CommunitySharesSection(
    sharedSaves: List<SharedSaveState>,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    SpTitledSection(title = "Community Saves", icon = Icons.Outlined.Share) {
        if (sharedSaves.isEmpty()) {
            Text(
                text = "No community saves yet. Be the first to share!",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
            )
        } else {
            sharedSaves.forEach { save ->
                SharedSaveItem(
                    sharedSave = save,
                    onDownload = { onDownload(save.id) },
                    onDelete = { onDelete(save.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.XSmall),
                )
            }
        }
    }
}

@Composable
private fun SaveStateItem(
    saveState: SaveState,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
    onUpdateNotes: ((String) -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Save State") },
            text = { Text("Are you sure you want to delete \"${saveState.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = SpColor.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showRenameDialog && onRename != null) {
        RenameDialog(
            currentName = saveState.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onRename(newName)
            },
        )
    }

    if (showNotesDialog && onUpdateNotes != null) {
        NotesDialog(
            currentNotes = saveState.notes ?: "",
            onDismiss = { showNotesDialog = false },
            onConfirm = { newNotes ->
                showNotesDialog = false
                onUpdateNotes(newNotes)
            },
        )
    }

    SpInnerCard(
        modifier = modifier,
        onClick = if (isSelectionMode) onToggleSelection else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default)
                .semantics {
                    contentDescription = "${saveState.name}, ${if (saveState.isAuto) "auto save" else "manual save"}"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection checkbox
            if (isSelectionMode && !saveState.isAuto) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection?.invoke() },
                    modifier = Modifier.padding(end = SpSpacing.Small),
                )
            }

            // Thumbnail or placeholder
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
                    .background(SpColor.SurfaceBright),
                contentAlignment = Alignment.Center,
            ) {
                if (saveState.screenshotUrl != null) {
                    AsyncImage(
                        model = saveState.screenshotUrl,
                        contentDescription = "Save state thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = if (saveState.isAuto) "A" else if (saveState.slot != null) "${saveState.slot}" else "S",
                        style = SpTypography.LabelLarge,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }

            Spacer(Modifier.width(SpSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    Text(
                        text = saveState.name,
                        style = SpTypography.TitleMedium,
                        color = SpColor.OnCard,
                    )
                    // Sync status icon
                    Icon(
                        imageVector = if (saveState.isSynced) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                        contentDescription = if (saveState.isSynced) "Synced" else "Local only",
                        tint = if (saveState.isSynced) SpColor.Success else SpColor.Warning,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = buildString {
                        append(if (saveState.isAuto) "Auto Save" else "Manual Save")
                        if (saveState.slot != null) append(" (Slot ${saveState.slot})")
                        saveState.createdAt?.let { append(" \u00b7 ${formatRelativeTime(it)}") }
                    },
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
                // Notes
                if (!saveState.notes.isNullOrBlank()) {
                    Text(
                        text = saveState.notes,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundSecondary,
                        maxLines = 2,
                        modifier = Modifier.padding(top = SpSpacing.XXSmall),
                    )
                }
            }

            if (!isSelectionMode) {
                // Rename button (not for auto-saves)
                if (!saveState.isAuto && onRename != null) {
                    IconButton(
                        onClick = { showRenameDialog = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Rename ${saveState.name}"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = SpColor.OnBackgroundTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.semantics {
                            contentDescription = "Delete ${saveState.name}"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    val isValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Save State") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("rename_input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = isValid,
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun NotesDialog(
    currentNotes: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var notes by remember { mutableStateOf(currentNotes) }
    val maxLength = 200

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Notes") },
        text = {
            Column {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= maxLength) notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth().testTag("notes_input"),
                    maxLines = 3,
                )
                Text(
                    text = "${notes.length}/$maxLength",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.align(Alignment.End).padding(top = SpSpacing.XXSmall),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(notes.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ChallengesSection(
    gameTitle: String,
    onViewAll: () -> Unit,
    onCreateChallenge: (() -> Unit)? = null,
) {
    SpTitledSection(title = "Challenges", icon = Icons.Outlined.Flag) {
        Text(
            text = "Compete on community-created challenges for $gameTitle",
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            SpButton(
                text = "View Challenges",
                onClick = onViewAll,
                style = SpButtonStyle.Secondary,
                modifier = Modifier.weight(1f).testTag("view_challenges_button"),
            )
            if (onCreateChallenge != null) {
                SpButton(
                    text = "Create New",
                    onClick = onCreateChallenge,
                    modifier = Modifier.weight(1f).testTag("create_challenge_button"),
                )
            }
        }
    }
}
