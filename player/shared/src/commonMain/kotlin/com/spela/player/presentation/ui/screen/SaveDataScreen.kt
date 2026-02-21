package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.SaveData
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SaveDataIntent
import com.spela.player.presentation.viewmodel.SaveDataViewModel

@Composable
fun SaveDataScreen(
    gameId: String,
    viewModel: SaveDataViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(gameId) {
        viewModel.onIntent(SaveDataIntent.Load(gameId))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SpTopBar(title = "Save Data", onBack = onBack)

        when {
            state.isLoading -> SpLoadingIndicator(modifier = Modifier.fillMaxSize())
            state.saveDataList.isEmpty() && state.error == null -> {
                SpEmptyState(
                    icon = Icons.Filled.Save,
                    title = "No save data",
                    message = "Play a game and your progress will be saved automatically",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = SpSpacing.Medium)
                        .semantics { contentDescription = "Save data list" },
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    items(state.saveDataList) { saveData ->
                        SaveDataItem(
                            saveData = saveData,
                            onActivate = { viewModel.onIntent(SaveDataIntent.Activate(saveData)) },
                            onRename = { viewModel.onIntent(SaveDataIntent.ShowRename(saveData)) },
                            onDelete = { viewModel.onIntent(SaveDataIntent.ShowDeleteConfirm(saveData)) },
                        )
                    }
                }
            }
        }
    }

    // Rename dialog
    if (state.showRenameDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(SaveDataIntent.DismissRename) },
            title = { Text("Rename Save Data") },
            text = {
                TextField(
                    value = state.renameText,
                    onValueChange = { viewModel.onIntent(SaveDataIntent.UpdateRenameText(it)) },
                    singleLine = true,
                    modifier = Modifier.semantics { contentDescription = "Rename input" },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(SaveDataIntent.ConfirmRename) }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(SaveDataIntent.DismissRename) }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Delete confirmation
    if (state.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(SaveDataIntent.DismissDelete) },
            title = { Text("Delete Save Data") },
            text = { Text("Delete \"${state.deleteTarget?.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onIntent(SaveDataIntent.ConfirmDelete) }) {
                    Text("Delete", color = SpColor.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(SaveDataIntent.DismissDelete) }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SaveDataItem(
    saveData: SaveData,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onActivate)
            .padding(vertical = SpSpacing.Small)
            .semantics { contentDescription = "Save data: ${saveData.name}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
            ) {
                Text(
                    text = saveData.name,
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackground,
                )
                if (saveData.isActive) {
                    Text(
                        text = "Active",
                        style = SpTypography.LabelSmall,
                        color = SpColor.Success,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                val sizeText = if (saveData.fileSize > 0) {
                    val kb = saveData.fileSize / 1024.0
                    if (kb >= 1024) "%.1f MB".format(kb / 1024) else "%.0f KB".format(kb)
                } else ""

                val timeText = saveData.updatedAt?.toString()?.take(19)?.replace('T', ' ') ?: ""

                if (sizeText.isNotEmpty()) {
                    Text(text = sizeText, style = SpTypography.LabelSmall, color = SpColor.OnBackgroundSecondary)
                }
                if (timeText.isNotEmpty()) {
                    Text(text = timeText, style = SpTypography.LabelSmall, color = SpColor.OnBackgroundSecondary)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall)) {
            TextButton(onClick = onRename) { Text("Rename", style = SpTypography.LabelSmall) }
            TextButton(onClick = onDelete) { Text("Delete", style = SpTypography.LabelSmall, color = SpColor.Error) }
        }
    }
}
