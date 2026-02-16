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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpTopBar
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
    val state by viewModel.state.collectAsState()

    LaunchedEffect(collectionId) {
        viewModel.onIntent(CollectionsIntent.LoadCollectionDetail(collectionId))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(
            title = state.selectedDetail?.name ?: "Collection",
            showBack = true,
            onBack = onBack,
        )

        if (state.isDetailLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SpLoadingIndicator(message = "Loading collection...")
            }
        } else if (state.selectedDetail != null) {
            val detail = state.selectedDetail!!
            LazyVerticalGrid(
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

                if (detail.games.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpEmptyStates.EmptyLibrary()
                        }
                    }
                } else {
                    items(detail.games, key = { it.id }) { game ->
                        GameGridItem(
                            game = game,
                            onClick = { onGameSelected(game.id) },
                        )
                    }
                }
            }
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
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XXSmall),
    )
}
