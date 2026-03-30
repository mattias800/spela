package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.domain.model.ScreenshotItem
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel

private const val SCREENSHOT_ASPECT_RATIO = 16f / 9f

@Composable
fun ExploreGalleryScreen(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.screenshotGalleryState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadScreenshotGallery()
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    SpScreen(modifier = Modifier.testTag("gallery_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(
                    title = "Screenshot Gallery",
                    showBack = true,
                    onBack = onBack,
                )
            }

            when {
                state.isLoading && state.screenshots.isEmpty() -> {
                    // Loading skeleton grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("gallery_loading"),
                        contentPadding = PaddingValues(SpSpacing.ScreenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        items(6) {
                            SpGameCardSkeleton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(SCREENSHOT_ASPECT_RATIO),
                            )
                        }
                    }
                }

                state.screenshots.isNotEmpty() -> {
                    val gridState = rememberLazyGridState()

                    // Load more when nearing the end
                    LaunchedEffect(gridState) {
                        snapshotFlow {
                            val layoutInfo = gridState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= totalItems - 4
                        }.collect { nearEnd ->
                            if (nearEnd && state.hasMore && !state.isLoading) {
                                viewModel.loadMoreScreenshots()
                            }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 200.dp),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("gallery_grid"),
                        contentPadding = PaddingValues(SpSpacing.ScreenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        items(
                            items = state.screenshots,
                            key = { "${it.gameId}_${it.url}" },
                        ) { screenshot ->
                            ScreenshotGridCard(
                                screenshot = screenshot,
                                onClick = { onGameSelected(screenshot.gameId) },
                                modifier = if (screenshot == state.screenshots.firstOrNull()) Modifier.autoFocus() else Modifier,
                            )
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.Photo,
                            title = "No screenshots yet",
                            message = "Screenshots will appear here once games have been enriched with metadata.",
                            modifier = Modifier.testTag("gallery_empty_state"),
                        )
                    }
                }
            }
        }

        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.dismissScreenshotGalleryError() },
                )
            },
            onDismiss = { viewModel.dismissScreenshotGalleryError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ScreenshotGridCard(
    screenshot: ScreenshotItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)

    SpCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gallery_screenshot_${screenshot.gameId}")
            .semantics {
                contentDescription = "${screenshot.gameTitle}, ${screenshot.consoleName}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape),
        ) {
            SubcomposeAsyncImage(
                model = screenshot.url,
                contentDescription = "${screenshot.gameTitle} screenshot",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(SCREENSHOT_ASPECT_RATIO),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                            .background(SpColor.SurfaceBright.copy(alpha = 0.25f)),
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                            .background(
                                Brush.linearGradient(
                                    listOf(SpColor.SurfaceVariant, SpColor.SurfaceElevated),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = screenshot.gameTitle.take(2).uppercase(),
                            style = SpTypography.DisplaySmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                },
            )

            // Overlay with game title at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(SCREENSHOT_ASPECT_RATIO)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpSpacing.Small),
                ) {
                    Text(
                        text = screenshot.gameTitle,
                        style = SpTypography.LabelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("gallery_title_${screenshot.gameId}"),
                    )
                    Text(
                        text = screenshot.consoleName,
                        style = SpTypography.LabelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
