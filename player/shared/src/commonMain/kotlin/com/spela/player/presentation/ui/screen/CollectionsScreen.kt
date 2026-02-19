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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GameCollection
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.feature.collections.CollectionFormDialog
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.CollectionsIntent
import com.spela.player.presentation.viewmodel.CollectionsViewModel

private val collectionTabs = listOf("My Collections", "Public")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onCollectionSelected: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            viewModel.onIntent(CollectionsIntent.LoadMyCollections)
        } else {
            viewModel.onIntent(CollectionsIntent.LoadPublicCollections)
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = SpColor.Surface,
                contentColor = SpColor.OnSurface,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = SpColor.Primary,
                        )
                    }
                },
            ) {
                collectionTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = SpTypography.LabelMedium,
                                color = if (selectedTab == index) SpColor.Primary else SpColor.OnBackgroundTertiary,
                            )
                        },
                    )
                }
            }

            val collections = if (selectedTab == 0) state.myCollections else state.publicCollections

            if (state.isLoading && collections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading collections...")
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = {
                        if (selectedTab == 0) {
                            viewModel.onIntent(CollectionsIntent.LoadMyCollections)
                        } else {
                            viewModel.onIntent(CollectionsIntent.LoadPublicCollections)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (collections.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selectedTab == 0) {
                                SpEmptyStates.NoCollections(
                                    onCreateCollection = {
                                        viewModel.onIntent(CollectionsIntent.ShowCreateDialog)
                                    },
                                )
                            } else {
                                SpEmptyStates.NoCollections()
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = SpSpacing.ScreenHorizontal,
                                vertical = SpSpacing.Default,
                            ),
                            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        ) {
                            items(collections, key = { it.id }) { collection ->
                                CollectionListItem(
                                    collection = collection,
                                    onClick = { onCollectionSelected(collection.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // FAB — only on "My Collections" tab
        if (selectedTab == 0) {
            FloatingActionButton(
                onClick = { viewModel.onIntent(CollectionsIntent.ShowCreateDialog) },
                containerColor = SpColor.Primary,
                contentColor = SpColor.OnPrimary,
                shape = RoundedCornerShape(SpSpacing.RadiusXLarge),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(SpSpacing.Default)
                    .semantics { contentDescription = "Create collection" },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                )
            }
        }

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
}

@Composable
private fun CollectionListItem(
    collection: GameCollection,
    onClick: () -> Unit,
) {
    SpCard(
        onClick = onClick,
        modifier = Modifier
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
