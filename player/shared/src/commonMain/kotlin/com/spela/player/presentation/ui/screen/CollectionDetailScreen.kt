package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.spela.player.presentation.ui.components.SpLazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.feature.library.GameGridItem
import com.spela.player.presentation.ui.components.SpIconButton
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSearchField
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.collections.CollectionFormDialog
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.CollectionsIntent
import com.spela.player.presentation.viewmodel.CollectionsViewModel

@Composable
fun CollectionDetailScreen(
    collectionId: String,
    viewModel: CollectionsViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    val isOwner = state.currentUserId.isNotEmpty() &&
        state.selectedDetail?.userId == state.currentUserId

    LaunchedEffect(collectionId) {
        viewModel.onIntent(CollectionsIntent.LoadCollectionDetail(collectionId))
    }

    // Navigate back after successful delete
    LaunchedEffect(state.collectionDeleted) {
        if (state.collectionDeleted) {
            onBack()
            viewModel.onIntent(CollectionsIntent.DismissCollectionDeleted)
        }
    }

    val focusMemory = rememberFocusMemoryState()

    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = state.selectedDetail?.name ?: "Collection",
                showBack = true,
                onBack = onBack,
                actions = {
                    if (isOwner) {
                        SpIconButton(
                            icon = Icons.Outlined.Edit,
                            contentDescription = "Edit collection",
                            onClick = { viewModel.onIntent(CollectionsIntent.ShowEditDialog) },
                        )
                        SpIconButton(
                            icon = Icons.Outlined.Delete,
                            contentDescription = "Delete collection",
                            onClick = { viewModel.onIntent(CollectionsIntent.ShowDeleteDialog) },
                        )
                    }
                },
            )

            if (state.isDetailLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading collection...")
                }
            } else if (state.selectedDetail != null) {
                val detail = state.selectedDetail ?: return@CompositionLocalProvider
                var searchQuery by rememberSaveable { mutableStateOf("") }
                val showSearch = detail.games.size > 5
                val filteredGames = if (searchQuery.isBlank()) {
                    detail.games
                } else {
                    detail.games.filter {
                        it.title.contains(searchQuery, ignoreCase = true)
                    }
                }

                SpLazyVerticalGrid(
                    columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = SpSpacing.ScreenHorizontal,
                        vertical = SpSpacing.Default,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                ) {
                    // Collection info header
                    if (!detail.description.isNullOrBlank()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = SpSpacing.Medium),
                            ) {
                                Text(
                                    text = detail.description,
                                    style = SpTypography.BodyMedium,
                                    color = SpColor.OnBackgroundSecondary,
                                )
                                Spacer(Modifier.height(SpSpacing.Small))
                                VisibilityBadge(isPublic = detail.isPublic)
                            }
                        }
                    }

                    // Search field for collections with >5 games
                    if (showSearch) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SpSearchField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = SpSpacing.Small),
                                placeholder = "Search in collection...",
                            )
                        }
                    }

                    if (filteredGames.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (searchQuery.isNotBlank()) {
                                    SpEmptyStates.NoSearchResults(query = searchQuery)
                                } else {
                                    SpEmptyStates.EmptyLibrary()
                                }
                            }
                        }
                    } else {
                        itemsIndexed(filteredGames, key = { _, g -> g.id }) { index, game ->
                            CollectionGameGridItem(
                                game = game,
                                onClick = { onGameSelected(game.id) },
                                onRemove = if (isOwner) {
                                    {
                                        viewModel.onIntent(
                                            CollectionsIntent.RemoveGameFromCollection(game.id)
                                        )
                                    }
                                } else null,
                                onRequestScrape = { viewModel.requestScrapeIfNeeded(it) },
                                modifier = Modifier.focusRestoreItem(
                                    key = "collection_game_${game.id}",
                                    isDefault = index == 0,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Edit Collection dialog
        if (state.showEditDialog) {
            state.selectedDetail?.let { detail ->
                CollectionFormDialog(
                    title = "Edit Collection",
                    confirmText = "Save",
                    isLoading = state.isUpdating,
                    initialName = detail.name,
                    initialDescription = detail.description ?: "",
                    initialIsPublic = detail.isPublic,
                    onDismiss = { viewModel.onIntent(CollectionsIntent.DismissEditDialog) },
                    onConfirm = { name, description, isPublic ->
                        viewModel.onIntent(
                            CollectionsIntent.UpdateCollection(name, description, isPublic)
                        )
                    },
                )
            }
        }

        // Delete Collection confirmation dialog
        if (state.showDeleteDialog) {
            state.selectedDetail?.let { detail ->
                SpConfirmDialog(
                    title = "Delete Collection",
                    message = "Are you sure you want to delete \"${detail.name}\"? This action cannot be undone.",
                    onDismiss = { viewModel.onIntent(CollectionsIntent.DismissDeleteDialog) },
                    onConfirm = {
                        viewModel.onIntent(CollectionsIntent.DismissDeleteDialog)
                        viewModel.onIntent(CollectionsIntent.DeleteCollection)
                    },
                    confirmText = "Delete",
                    isDestructive = true,
                )
            }
        }

        // Success snackbar
        SpSnackbar(
            data = state.successMessage?.let {
                SpSnackbarData(message = it, type = SpSnackbarType.Success)
            },
            onDismiss = { viewModel.onIntent(CollectionsIntent.DismissSuccess) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Error snackbar
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(CollectionsIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(CollectionsIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    } // CompositionLocalProvider
}

@Composable
private fun CollectionGameGridItem(
    game: Game,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    onRequestScrape: ((Game) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        GameGridItem(game = game, onClick = onClick, onRequestScrape = onRequestScrape)
        if (onRemove != null) {
            SpIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Remove ${game.title} from collection",
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun VisibilityBadge(isPublic: Boolean) {
    val label = if (isPublic) "Public" else "Private"
    val bgColor = if (isPublic) SpColor.PrimaryContainer else SpColor.SurfaceVariant

    Text(
        text = label,
        style = SpTypography.LabelSmall,
        color = SpColor.OnBackgroundSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
            .background(bgColor)
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XXSmall),
    )
}
