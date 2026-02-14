package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SharedSaveState
import com.spela.player.presentation.intent.GameDetailIntent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Icon
import com.spela.player.presentation.ui.components.GameDetailLayout
import com.spela.player.presentation.ui.components.GameDetailSkeleton
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpConsoleChip
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpHeroCover
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.social.SharedSaveItem
import com.spela.player.presentation.ui.components.social.StarRatingRow
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameDetailViewModel

@Composable
fun GameDetailScreen(
    gameId: String,
    viewModel: GameDetailViewModel,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(gameId) {
        viewModel.onIntent(GameDetailIntent.LoadGame(gameId))
    }

    if (state.isLoading && state.gameDetail == null) {
        GameDetailSkeleton(onBack = onBack)
        return
    }

    val detail = state.gameDetail ?: return
    val game = detail.game

    Box(modifier = Modifier.fillMaxSize()) {
        GameDetailLayout(
            topBar = {
                SpTopBar(
                    title = "",
                    showBack = true,
                    onBack = onBack,
                    modifier = Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                SpColor.Background.copy(alpha = 0.7f),
                                SpColor.Background.copy(alpha = 0f),
                            ),
                        )
                    ),
                )
            },
            cover = { modifier ->
                SpHeroCover(
                    imageUrl = game.coverUrl,
                    contentDescription = "${game.title} cover art",
                    modifier = modifier,
                )
            },
            sections = {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SpSpacing.ScreenHorizontal),
                    ) {
                        GameInfoContent(
                            gameId = gameId,
                            game = game,
                            detail = detail,
                            state = state,
                            viewModel = viewModel,
                            onPlay = onPlay,
                        )
                    }
                }

                item {
                    ScreenshotsSection(detail.screenshots)
                }

                item {
                    Column(
                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                    ) {
                        SaveStatesSection(state.saveStates)
                    }
                }

                item {
                    Column(
                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                    ) {
                        CommunitySharesSection(
                            sharedSaves = state.sharedSaves,
                            onDownload = { saveId ->
                                viewModel.onIntent(GameDetailIntent.DownloadSharedSave(saveId))
                            },
                            onDelete = { saveId ->
                                viewModel.onIntent(GameDetailIntent.DeleteSharedSave(saveId))
                            },
                        )
                    }
                }
            },
        )

        // Error snackbar
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(GameDetailIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(GameDetailIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun GameInfoContent(
    gameId: String,
    game: com.spela.player.domain.model.Game,
    detail: com.spela.player.domain.model.GameDetail,
    state: com.spela.player.presentation.state.GameDetailState,
    viewModel: GameDetailViewModel,
    onPlay: (String) -> Unit,
) {
    Text(
        text = game.title,
        style = SpTypography.DisplaySmall,
        color = SpColor.OnBackground,
        modifier = Modifier.semantics { heading() },
    )

    Spacer(Modifier.height(SpSpacing.Small))

    Row(
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpConsoleChip(
            consoleName = game.consoleName,
            consoleColor = getConsoleColor(game.consoleName),
        )
        game.genre?.let { SpChip(text = it) }
        game.releaseDate?.let { SpChip(text = it) }
    }

    if (state.isScraping) {
        Spacer(Modifier.height(SpSpacing.Small))
        Text(
            text = "Fetching game info...",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }

    Spacer(Modifier.height(SpSpacing.XLarge))

    // Action buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        if (state.isGameCached) {
            SpButton(
                text = "Play",
                onClick = { onPlay(gameId) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Play ${game.title}" },
            )
        } else {
            val isDownloading = state.downloadProgress?.state == DownloadState.DOWNLOADING
            SpButton(
                text = if (isDownloading) "Downloading..." else "Download",
                onClick = { viewModel.onIntent(GameDetailIntent.DownloadGame) },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = if (isDownloading) "Downloading ${game.title}"
                        else "Download ${game.title}"
                    },
                isLoading = isDownloading,
                enabled = !isDownloading,
            )
        }

        SpButton(
            text = "",
            onClick = { viewModel.onIntent(GameDetailIntent.ToggleFavorite) },
            style = if (game.isFavorite) SpButtonStyle.Secondary else SpButtonStyle.Outlined,
            modifier = Modifier.semantics {
                contentDescription = if (game.isFavorite) "Remove from favorites" else "Add to favorites"
                role = Role.Button
            },
            leadingIcon = {
                Icon(
                    imageVector = if (game.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
        )

        SpButton(
            text = "",
            onClick = { viewModel.onIntent(GameDetailIntent.TogglePlayLater) },
            style = if (game.isInPlayLater) SpButtonStyle.Secondary else SpButtonStyle.Outlined,
            modifier = Modifier.semantics {
                contentDescription = if (game.isInPlayLater) "Remove from Play Later" else "Add to Play Later"
                role = Role.Button
            },
            leadingIcon = {
                Icon(
                    imageVector = if (game.isInPlayLater) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
        )
    }

    // Download progress
    AnimatedVisibility(
        visible = state.downloadProgress?.state == DownloadState.DOWNLOADING,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        state.downloadProgress?.let { progress ->
            Column(modifier = Modifier.padding(top = SpSpacing.Medium)) {
                SpProgressBar(
                    progress = progress.progress,
                    showPercentage = true,
                    label = "Downloading...",
                )
            }
        }
    }

    Spacer(Modifier.height(SpSpacing.XLarge))

    // Description
    game.description?.let { description ->
        Text(
            text = "About",
            style = SpTypography.HeadlineSmall,
            color = SpColor.OnBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(SpSpacing.Small))
        Text(
            text = description,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
        Spacer(Modifier.height(SpSpacing.XLarge))
    }

    // Developer / Publisher
    if (game.developer != null || game.publisher != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXLarge),
        ) {
            game.developer?.let { InfoColumn(label = "Developer", value = it) }
            game.publisher?.let { InfoColumn(label = "Publisher", value = it) }
        }
        Spacer(Modifier.height(SpSpacing.XLarge))
    }

    // Rating
    StarRatingRow(
        currentRating = state.myRating,
        averageRating = state.ratingSummary?.averageRating ?: game.averageRating,
        ratingCount = state.ratingSummary?.totalRatings ?: game.ratingCount,
        onRate = { rating ->
            viewModel.onIntent(GameDetailIntent.RateGame(rating))
        },
    )
    Spacer(Modifier.height(SpSpacing.XLarge))
}

@Composable
private fun ScreenshotsSection(screenshots: List<String>) {
    if (screenshots.isEmpty()) return

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
        items(screenshots) { screenshotUrl ->
            SpCard(
                modifier = Modifier
                    .width(240.dp)
                    .height(160.dp),
            ) {
                coil3.compose.AsyncImage(
                    model = screenshotUrl,
                    contentDescription = "Game screenshot",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
    }
    Spacer(Modifier.height(SpSpacing.XLarge))
}

@Composable
private fun SaveStatesSection(saveStates: List<SaveState>) {
    Text(
        text = "Save States",
        style = SpTypography.HeadlineSmall,
        color = SpColor.OnBackground,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(SpSpacing.Medium))

    if (saveStates.isEmpty()) {
        SpEmptyStates.NoSaveStates(modifier = Modifier.fillMaxWidth())
    } else {
        saveStates.forEach { save ->
            SaveStateItem(
                saveState = save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.XSmall),
            )
        }
    }
}

@Composable
private fun InfoColumn(label: String, value: String) {
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
private fun CommunitySharesSection(
    sharedSaves: List<SharedSaveState>,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Spacer(Modifier.height(SpSpacing.XLarge))
    Text(
        text = "Community Saves",
        style = SpTypography.HeadlineSmall,
        color = SpColor.OnBackground,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(SpSpacing.Medium))

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

@Composable
private fun SaveStateItem(
    saveState: SaveState,
    modifier: Modifier = Modifier,
) {
    SpCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium)
                .semantics {
                    contentDescription = "${saveState.name}, ${if (saveState.isAuto) "auto save" else "manual save"}"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SpColor.SurfaceBright),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (saveState.isAuto) "A" else "S",
                    style = SpTypography.LabelLarge,
                    color = SpColor.OnBackgroundTertiary,
                )
            }

            Spacer(Modifier.width(SpSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = saveState.name,
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnCard,
                )
                Text(
                    text = if (saveState.isAuto) "Auto Save" else "Manual Save",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }
    }
}
