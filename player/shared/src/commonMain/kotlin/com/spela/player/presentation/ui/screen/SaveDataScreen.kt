package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.SaveData
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.library.darken
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
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
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(gameId) {
        viewModel.onIntent(SaveDataIntent.Load(gameId))
    }

    // Dialogs
    if (state.showRenameDialog) {
        SpDialog(
            title = "Rename Save Data",
            onDismiss = { viewModel.onIntent(SaveDataIntent.DismissRename) },
            onConfirm = { viewModel.onIntent(SaveDataIntent.ConfirmRename) },
            confirmText = "Rename",
        ) {
            SpTextField(
                value = state.renameText,
                onValueChange = { viewModel.onIntent(SaveDataIntent.UpdateRenameText(it)) },
                label = "Name",
                placeholder = "Enter save name",
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Rename input" },
            )
        }
    }

    if (state.showDeleteConfirm) {
        SpConfirmDialog(
            title = "Delete Save Data",
            message = "Delete \"${state.deleteTarget?.name}\"? This cannot be undone.",
            onDismiss = { viewModel.onIntent(SaveDataIntent.DismissDelete) },
            onConfirm = { viewModel.onIntent(SaveDataIntent.ConfirmDelete) },
            confirmText = "Delete",
            isDestructive = true,
        )
    }

    val titleBarInset = LocalTitleBarInset.current
    val gradientColors = listOf(
        SpColor.PrimaryDark.darken(0.74f),
        SpColor.SecondaryDark.darken(0.82f),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val d = (size.width + size.height) * 0.25f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset(cx - d, cy - d),
                        end = Offset(cx + d, cy + d),
                    ),
                )
            },
    ) {
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
                        .semantics { contentDescription = "Save data list" },
                    contentPadding = PaddingValues(
                        start = SpSpacing.ScreenHorizontal,
                        end = SpSpacing.ScreenHorizontal,
                        top = titleBarInset + SpSpacing.TopBarHeight + SpSpacing.Default,
                        bottom = SpSpacing.Default,
                    ),
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

        // Fixed top bar overlaid on top of scrollable content
        SpTopBar(
            title = "Save Data",
            showBack = true,
            onGradient = true,
            onBack = onBack,
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
    SpCard(
        onClick = onActivate,
        onGradient = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SpSpacing.Default,
                    vertical = SpSpacing.Medium,
                )
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
                        color = SpColor.OnCard,
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
}
