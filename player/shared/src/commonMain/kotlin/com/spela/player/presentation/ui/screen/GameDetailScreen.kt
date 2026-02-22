package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.NETPLAY_SUPPORTED_CONSOLES
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.state.AchievementsViewMode
import com.spela.player.presentation.state.GameDetailState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Icon
import com.spela.player.presentation.ui.feature.collections.CollectionPickerDialog
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.ui.feature.gamedetail.BiosWarningChip
import com.spela.player.presentation.ui.feature.gamedetail.ChallengesSection
import com.spela.player.presentation.ui.feature.gamedetail.CreateChallengeDialog
import com.spela.player.presentation.ui.feature.gamedetail.CommunitySharesSection
import com.spela.player.presentation.ui.feature.gamedetail.GameAchievementsSection
import com.spela.player.presentation.ui.feature.gamedetail.GameControlsSection
import com.spela.player.presentation.ui.feature.gamedetail.GameCommunityStatsSection
import com.spela.player.presentation.ui.feature.gamedetail.GameRelaysSection
import com.spela.player.presentation.ui.feature.gamedetail.GameReviewsSection
import com.spela.player.presentation.ui.feature.gamedetail.InfoColumn
import com.spela.player.presentation.ui.feature.gamedetail.SaveStatesSection
import com.spela.player.presentation.ui.feature.gamedetail.ScreenshotsSection
import com.spela.player.presentation.ui.feature.library.getConsoleColor
import com.spela.player.presentation.ui.components.GameDetailLayout
import com.spela.player.presentation.ui.components.GameDetailSkeleton
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpConsoleChip
import com.spela.player.presentation.ui.components.SpHeroCover
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpSplitButton
import com.spela.player.presentation.ui.components.SpSplitButtonMenuItem
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.social.StarRatingRow
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameDetailViewModel
import com.spela.player.presentation.viewmodel.KeyMappingViewModel

@Composable
fun GameDetailScreen(
    gameId: String,
    viewModel: GameDetailViewModel,
    keyMappingViewModel: KeyMappingViewModel? = null,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onPlayFresh: ((String) -> Unit)? = null,
    onCreateNetplay: ((String) -> Unit)? = null,
    onNavigateToChallenges: ((gameId: String, gameTitle: String) -> Unit)? = null,
    onNavigateToRelay: ((relayId: String) -> Unit)? = null,
    onNavigateToSaveData: ((gameId: String) -> Unit)? = null,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    val keyMappingState = keyMappingViewModel?.state?.collectAsState()

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
                // Read coverUrl from state delegate (not snapshot) so
                // the LazyColumn item recomposes when it changes after scraping.
                SpHeroCover(
                    imageUrl = state.gameDetail?.game?.coverUrl,
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
                            hasSaves = state.saveStates.isNotEmpty(),
                            missingBiosFiles = state.missingBiosFiles,
                            onPlay = onPlay,
                            onPlayFresh = onPlayFresh,
                            onDownloadGame = { viewModel.onIntent(GameDetailIntent.DownloadGame) },
                            onToggleFavorite = { viewModel.onIntent(GameDetailIntent.ToggleFavorite) },
                            onTogglePlayLater = { viewModel.onIntent(GameDetailIntent.TogglePlayLater) },
                            onAddToCollection = { viewModel.onIntent(GameDetailIntent.ShowAddToCollectionDialog) },
                            onRate = { rating -> viewModel.onIntent(GameDetailIntent.RateGame(rating)) },
                            onCreateNetplay = onCreateNetplay,
                            onDeleteLocalGame = { viewModel.onIntent(GameDetailIntent.ShowDeleteDownloadDialog) },
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
                        SaveStatesSection(
                            saveStates = state.saveStates,
                            onDelete = { saveId ->
                                viewModel.onIntent(GameDetailIntent.DeleteSave(saveId))
                            },
                        )
                    }
                }

                // Save Data (SRAM) section
                if (onNavigateToSaveData != null && state.saveDataCount > 0) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                        ) {
                            Spacer(Modifier.height(SpSpacing.Medium))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Save Data",
                                    style = SpTypography.HeadlineSmall,
                                    color = SpColor.OnBackground,
                                )
                                SpButton(
                                    text = "Manage (${state.saveDataCount})",
                                    onClick = { onNavigateToSaveData(gameId) },
                                    style = SpButtonStyle.Ghost,
                                )
                            }
                        }
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

                // Controls section (per-game key mapping override)
                if (keyMappingViewModel != null && keyMappingState != null) {
                    item {
                        val consoleId = game.consoleId
                        LaunchedEffect(gameId, consoleId) {
                            keyMappingViewModel.onIntent(
                                KeyMappingIntent.LoadGameMapping(gameId, consoleId)
                            )
                        }

                        Column(
                            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                        ) {
                            GameControlsSection(
                                gameId = gameId,
                                hasGameOverride = keyMappingState.value.hasGameOverride,
                                onEnableOverride = {
                                    keyMappingViewModel.onIntent(
                                        KeyMappingIntent.SaveAsGameOverride(gameId)
                                    )
                                },
                                onClearOverride = {
                                    keyMappingViewModel.onIntent(
                                        KeyMappingIntent.ClearGameOverride(gameId)
                                    )
                                },
                                onEditMapping = {
                                    // For now, re-load the mapping so user can see it
                                    keyMappingViewModel.onIntent(
                                        KeyMappingIntent.LoadGameMapping(gameId, consoleId)
                                    )
                                },
                            )
                        }
                    }
                }

                if (onNavigateToChallenges != null) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                        ) {
                            ChallengesSection(
                                gameTitle = game.title,
                                onViewAll = { onNavigateToChallenges(gameId, game.title) },
                                onCreateChallenge = {
                                    viewModel.onIntent(GameDetailIntent.ShowCreateChallengeDialog)
                                },
                            )
                        }
                    }
                }

                // Community Stats
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                    ) {
                        GameCommunityStatsSection(
                            stats = state.gameStats,
                            isLoading = state.isLoadingStats,
                        )
                    }
                }

                // Achievements
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                    ) {
                        GameAchievementsSection(
                            achievements = state.achievements,
                            progress = state.achievementProgress,
                            timeline = state.achievementTimeline,
                            leaderboard = state.achievementLeaderboard,
                            viewMode = state.achievementsView,
                            isLoading = state.isLoadingAchievements,
                            onToggleView = { mode ->
                                viewModel.onIntent(GameDetailIntent.ToggleAchievementsView(mode))
                            },
                            achievementsWarning = game.achievementsWarning,
                        )
                    }
                }

                // Reviews
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                    ) {
                        GameReviewsSection(
                            reviews = state.reviews,
                            reviewsTotal = state.reviewsTotal,
                            reviewsPage = state.reviewsPage,
                            isLoading = state.isLoadingReviews,
                            onLoadMore = {
                                viewModel.onIntent(GameDetailIntent.LoadMoreReviews(gameId))
                            },
                        )
                    }
                }

                // Active Relays
                if (onNavigateToRelay != null) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                        ) {
                            GameRelaysSection(
                                relays = state.gameRelays,
                                isLoading = state.isLoadingRelays,
                                onRelayClick = { relayId -> onNavigateToRelay(relayId) },
                            )
                        }
                    }
                }

                // Bottom spacing
                item {
                    Spacer(Modifier.height(SpSpacing.XXLarge))
                }
            },
        )

        // Delete Download confirmation dialog
        if (state.showDeleteDownloadDialog) {
            SpConfirmDialog(
                title = "Delete Download",
                message = "Remove the downloaded game files from this device? Your save states on the server are not affected.",
                onDismiss = { viewModel.onIntent(GameDetailIntent.DismissDeleteDownloadDialog) },
                onConfirm = { viewModel.onIntent(GameDetailIntent.DeleteLocalGame) },
                confirmText = "Delete",
                isDestructive = true,
            )
        }

        // Create Challenge dialog
        if (state.showCreateChallengeDialog) {
            CreateChallengeDialog(
                gameTitle = game.title,
                saveStates = state.saveStates,
                isSubmitting = state.isCreatingChallenge,
                onSubmit = { saveStateId, name, description, type, difficulty ->
                    viewModel.onIntent(
                        GameDetailIntent.CreateChallenge(saveStateId, name, description, type, difficulty)
                    )
                },
                onDismiss = { viewModel.onIntent(GameDetailIntent.DismissCreateChallengeDialog) },
            )
        }

        // Collection picker dialog
        if (state.showAddToCollectionDialog) {
            CollectionPickerDialog(
                collections = state.userCollections,
                isLoading = state.isLoadingCollections,
                onDismiss = { viewModel.onIntent(GameDetailIntent.DismissAddToCollectionDialog) },
                onSelectCollection = { collectionId ->
                    viewModel.onIntent(GameDetailIntent.AddToCollection(collectionId))
                },
                onCreateCollectionAndAddGame = { name ->
                    viewModel.onIntent(GameDetailIntent.CreateCollectionAndAddGame(name))
                },
                isCreatingCollection = state.isCreatingCollection,
                collectionCreationError = state.collectionCreationError,
            )
        }

        // Success snackbar
        SpSnackbar(
            data = state.successMessage?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Success,
                )
            },
            onDismiss = { viewModel.onIntent(GameDetailIntent.DismissSuccess) },
            modifier = Modifier.align(Alignment.BottomCenter),
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
    game: Game,
    detail: GameDetail,
    state: GameDetailState,
    hasSaves: Boolean,
    missingBiosFiles: List<BiosMissingFile> = emptyList(),
    onPlay: (String) -> Unit,
    onPlayFresh: ((String) -> Unit)? = null,
    onDownloadGame: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayLater: () -> Unit,
    onAddToCollection: () -> Unit,
    onRate: (Int) -> Unit,
    onCreateNetplay: ((String) -> Unit)? = null,
    onDeleteLocalGame: () -> Unit = {},
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
        game.genre?.takeIf { it.isNotBlank() }?.let { SpChip(text = it) }
        game.releaseDate?.takeIf { it.isNotBlank() }?.let { SpChip(text = it) }
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

    // Action buttons - two-row layout
    // Row 1: Play/Download split button with overflow menu
    val supportsNetplay = game.consoleId.lowercase() in NETPLAY_SUPPORTED_CONSOLES

    if (state.isGameCached) {
        val menuItems = buildList {
            if (hasSaves && onPlayFresh != null) {
                add(SpSplitButtonMenuItem("New Game") { onPlayFresh(gameId) })
            }
            if (onCreateNetplay != null && supportsNetplay) {
                add(SpSplitButtonMenuItem("Netplay") { onCreateNetplay(gameId) })
            }
            add(SpSplitButtonMenuItem("Delete Download") { onDeleteLocalGame() })
        }

        SpSplitButton(
            text = if (hasSaves) "Resume" else "Play",
            onClick = { onPlay(gameId) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (hasSaves) "Resume ${game.title}" else "Play ${game.title}"
                },
            menuItems = menuItems,
        )
    } else {
        val isActivelyDownloading = state.downloadProgress?.state == DownloadState.DOWNLOADING
        val isBusy = state.isDownloading || isActivelyDownloading

        val menuItems = buildList {
            if (onCreateNetplay != null && supportsNetplay) {
                add(SpSplitButtonMenuItem("Netplay") { onCreateNetplay(gameId) })
            }
        }

        SpSplitButton(
            text = if (isBusy) "Downloading..." else "Download",
            onClick = onDownloadGame,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (isBusy) "Downloading ${game.title}"
                    else "Download ${game.title}"
                },
            isLoading = isBusy,
            enabled = !isBusy,
            menuItems = menuItems,
        )
    }

    // BIOS warning chip (AC 4.4)
    if (missingBiosFiles.isNotEmpty()) {
        var showBiosInfo by remember { mutableStateOf(false) }

        Spacer(Modifier.height(SpSpacing.Small))
        BiosWarningChip(
            missingFiles = missingBiosFiles,
            onClick = { showBiosInfo = !showBiosInfo },
        )
        AnimatedVisibility(visible = showBiosInfo) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SpSpacing.Small)
                    .background(
                        SpColor.Warning.copy(alpha = 0.1f),
                        RoundedCornerShape(SpSpacing.RadiusMedium),
                    )
                    .padding(SpSpacing.Medium)
                    .semantics { contentDescription = "Missing BIOS files info" },
            ) {
                Text(
                    text = "Missing BIOS files:",
                    style = SpTypography.LabelMedium,
                    color = SpColor.Warning,
                )
                Spacer(Modifier.height(SpSpacing.XSmall))
                missingBiosFiles.forEach { file ->
                    Text(
                        text = file.fileName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundSecondary,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(SpSpacing.Small))

    // Row 2: Icon action buttons
    Row(
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        SpButton(
            text = "",
            onClick = onToggleFavorite,
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
            onClick = onTogglePlayLater,
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

        SpButton(
            text = "",
            onClick = onAddToCollection,
            style = SpButtonStyle.Outlined,
            modifier = Modifier.semantics {
                contentDescription = "Add to collection"
                role = Role.Button
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.LibraryAdd,
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
                if (progress.totalDiscs > 1) {
                    Text(
                        text = "Downloading Disc ${progress.currentDisc} of ${progress.totalDiscs}",
                        style = SpTypography.LabelMedium,
                        color = SpColor.OnBackgroundSecondary,
                        modifier = Modifier.padding(bottom = SpSpacing.XSmall),
                    )
                }
                SpProgressBar(
                    progress = if (progress.isIndeterminate) 0f else progress.progress,
                    showPercentage = !progress.isIndeterminate,
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
            onRate(rating)
        },
    )
    Spacer(Modifier.height(SpSpacing.XLarge))
}
