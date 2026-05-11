package com.spela.player.presentation.ui.screen

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
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.TestTags
import com.spela.player.domain.model.GameCollection
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpFab
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.feature.collections.CollectionFormDialog
import com.spela.player.presentation.ui.feature.library.darken
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.CollectionsIntent
import com.spela.player.presentation.viewmodel.CollectionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onCollectionSelected: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val titleBarInset = LocalTitleBarInset.current
    val focusMemory = rememberFocusMemoryState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(CollectionsIntent.LoadMyCollections)
        viewModel.onIntent(CollectionsIntent.LoadPublicCollections)
    }

    // Create Collection dialog
    if (state.showCreateDialog) {
        CollectionFormDialog(
            title = "Create Collection",
            confirmText = "Create",
            isLoading = state.isCreating,
            onDismiss = { viewModel.onIntent(CollectionsIntent.DismissCreateDialog) },
            onConfirm = { name, description, isPublic ->
                viewModel.onIntent(CollectionsIntent.CreateCollection(name, description, isPublic))
            },
        )
    }

    val gradientColors = listOf(
        SpColor.Secondary.darken(0.78f),
        SpColor.PrimaryDark.darken(0.72f),
    )

    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
    Box(modifier = Modifier.fillMaxSize().testTag(TestTags.SCREEN_COLLECTIONS)) {
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
            if (state.isLoading && state.myCollections.isEmpty() && state.publicCollections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ScreenLoadingIndicator(message = "Loading collections...")
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = {
                        viewModel.onIntent(CollectionsIntent.LoadMyCollections)
                        viewModel.onIntent(CollectionsIntent.LoadPublicCollections)
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.myCollections.isEmpty() && state.publicCollections.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(TestTags.COLLECTIONS_EMPTY_STATE),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpEmptyStates.NoCollections(
                                onCreateCollection = {
                                    viewModel.onIntent(CollectionsIntent.ShowCreateDialog)
                                },
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag(TestTags.COLLECTIONS_LIST),
                            contentPadding = PaddingValues(
                                start = SpSpacing.ScreenHorizontal,
                                end = SpSpacing.ScreenHorizontal,
                                top = titleBarInset + SpSpacing.Default,
                                bottom = SpSpacing.Default,
                            ),
                            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        ) {
                            if (state.myCollections.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "My Collections",
                                        style = SpTypography.TitleLarge,
                                        color = SpColor.OnBackground,
                                        modifier = Modifier
                                            .padding(vertical = SpSpacing.Small)
                                            .testTag(TestTags.COLLECTIONS_MY_HEADER),
                                    )
                                }
                                itemsIndexed(state.myCollections, key = { _, c -> "my-${c.id}" }) { index, collection ->
                                    CollectionListItem(
                                        collection = collection,
                                        onClick = { onCollectionSelected(collection.id) },
                                        modifier = Modifier.focusRestoreItem(
                                            key = "collection_my_${collection.id}",
                                            isDefault = index == 0,
                                        ),
                                    )
                                }
                            }

                            if (state.publicCollections.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Public Collections",
                                        style = SpTypography.TitleLarge,
                                        color = SpColor.OnBackground,
                                        modifier = Modifier
                                            .padding(
                                                top = if (state.myCollections.isNotEmpty()) SpSpacing.Large else SpSpacing.Small,
                                                bottom = SpSpacing.Small,
                                            )
                                            .testTag(TestTags.COLLECTIONS_PUBLIC_HEADER),
                                    )
                                }
                                itemsIndexed(state.publicCollections, key = { _, c -> "public-${c.id}" }) { index, collection ->
                                    CollectionListItem(
                                        collection = collection,
                                        onClick = { onCollectionSelected(collection.id) },
                                        modifier = Modifier.focusRestoreItem(
                                            key = "collection_public_${collection.id}",
                                            isDefault = state.myCollections.isEmpty() && index == 0,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB
        SpFab(
            icon = Icons.Filled.Add,
            onClick = { viewModel.onIntent(CollectionsIntent.ShowCreateDialog) },
            description = "Create collection",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(SpSpacing.Default),
        )

        // Success snackbar
        SpSnackbar(
            data = state.successMessage?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Success,
                )
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
private fun CollectionListItem(
    collection: GameCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onClick = onClick,
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${collection.name}, ${collection.gameCount} games"
                role = Role.Button
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = collection.coverUrl,
                contentDescription = "${collection.name} cover",
                modifier = Modifier.size(width = 60.dp, height = 60.dp),
                cornerRadius = SpSpacing.RadiusMedium,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collection.name,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = "${collection.gameCount} games",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
                if (!collection.description.isNullOrBlank()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = collection.description,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
