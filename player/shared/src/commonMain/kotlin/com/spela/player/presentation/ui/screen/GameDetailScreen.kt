package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.NETPLAY_SUPPORTED_CONSOLES
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.state.GameDetailState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import com.spela.player.presentation.ui.feature.collections.CollectionPickerDialog
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.ui.feature.gamedetail.BiosWarningChip
import com.spela.player.presentation.ui.feature.gamedetail.GameActionsMenu
import com.spela.player.presentation.ui.feature.gamedetail.MetadataGrid
import com.spela.player.presentation.ui.feature.gamedetail.VerificationChip
import com.spela.player.presentation.ui.feature.gamedetail.ChallengesSection
import com.spela.player.presentation.ui.feature.gamedetail.CreateChallengeDialog
import com.spela.player.presentation.ui.feature.gamedetail.CommunitySharesSection
import com.spela.player.presentation.ui.feature.gamedetail.GameAchievementsSection
import com.spela.player.presentation.ui.feature.gamedetail.GameControlsSection
import com.spela.player.presentation.ui.feature.gamedetail.GameCommunityStatsSection
import com.spela.player.presentation.ui.feature.gamedetail.GameRelaysSection
import com.spela.player.presentation.ui.feature.gamedetail.GameReviewsSection
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
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpSplitButton
import com.spela.player.presentation.ui.components.SpSplitButtonMenuItem
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.social.StarRatingRow
import com.spela.player.presentation.ui.components.social.formatRelativeTime
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameDetailViewModel
import com.spela.player.presentation.viewmodel.KeyMappingViewModel
import com.spela.player.util.formatPlayTime

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPortraitScreen = maxWidth <= maxHeight
        GameDetailLayout(
            topBar = {
                SpTopBar(
                    title = "",
                    showBack = true,
                    onBack = onBack,
                )
            },
            coverArt = { modifier, isPortrait ->
                // Read coverUrl from state delegate (not snapshot) so
                // the LazyColumn item recomposes when it changes after scraping.
                SpCoverArt(
                    imageUrl = state.gameDetail?.game?.coverUrl,
                    contentDescription = "${game.title} cover art",
                    modifier = modifier,
                    aspectRatio = if (isPortrait) null else 0.714f,
                )
            },
            coverExtra = { isPortrait ->
                // User rating below the cover (landscape only; portrait shows it inline)
                if (!isPortrait) {
                    StarRatingRow(
                        currentRating = state.myRating,
                        averageRating = state.ratingSummary?.averageRating ?: game.averageRating,
                        ratingCount = state.ratingSummary?.totalRatings ?: game.ratingCount,
                        onRate = { rating ->
                            viewModel.onIntent(GameDetailIntent.RateGame(rating))
                        },
                    )
                }
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
                            isPortrait = isPortraitScreen,
                            hasSaves = state.saveStates.isNotEmpty(),
                            missingBiosFiles = state.missingBiosFiles,
                            onPlay = onPlay,
                            onPlayFresh = onPlayFresh,
                            onDownloadGame = { viewModel.onIntent(GameDetailIntent.DownloadGame) },
                            onToggleFavorite = { viewModel.onIntent(GameDetailIntent.ToggleFavorite) },
                            onTogglePlayLater = { viewModel.onIntent(GameDetailIntent.TogglePlayLater) },
                            onAddToCollection = { viewModel.onIntent(GameDetailIntent.ShowAddToCollectionDialog) },
                            onCreateNetplay = onCreateNetplay,
                            onDeleteLocalGame = { viewModel.onIntent(GameDetailIntent.ShowDeleteDownloadDialog) },
                            onRate = { rating ->
                                viewModel.onIntent(GameDetailIntent.RateGame(rating))
                            },
                        )
                    }
                }

                // Section ordering matches web UI:

                // 1. Community Stats
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

                // Your Rating (portrait only — landscape shows it under cover art)
                if (isPortraitScreen) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.height(SpSpacing.XXLarge))
                            StarRatingRow(
                                currentRating = state.myRating,
                                averageRating = state.ratingSummary?.averageRating ?: game.averageRating,
                                ratingCount = state.ratingSummary?.totalRatings ?: game.ratingCount,
                                onRate = { rating ->
                                    viewModel.onIntent(GameDetailIntent.RateGame(rating))
                                },
                            )
                        }
                    }
                }

                // 2. Reviews
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

                // 3. Screenshots
                item {
                    ScreenshotsSection(detail.screenshots)
                }

                // 4. Achievements
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

                // 5. Save States
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

                // 6. Save Data (SRAM) - app-specific
                if (onNavigateToSaveData != null && state.saveDataCount > 0) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                        ) {
                            Spacer(Modifier.height(SpSpacing.XXLarge))
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

                // 7. Community Shares
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

                // 8. Game Controls - app-specific
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
                                    keyMappingViewModel.onIntent(
                                        KeyMappingIntent.LoadGameMapping(gameId, consoleId)
                                    )
                                },
                            )
                        }
                    }
                }

                // 9. Challenges
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

                // 10. Active Relays
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
    isPortrait: Boolean = false,
    hasSaves: Boolean,
    missingBiosFiles: List<BiosMissingFile> = emptyList(),
    onPlay: (String) -> Unit,
    onPlayFresh: ((String) -> Unit)? = null,
    onDownloadGame: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayLater: () -> Unit,
    onAddToCollection: () -> Unit,
    onCreateNetplay: ((String) -> Unit)? = null,
    onDeleteLocalGame: () -> Unit = {},
    onRate: (Int) -> Unit = {},
) {
    // Title row with trophy icon if achievements exist
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        Text(
            text = game.title,
            style = SpTypography.DisplaySmall,
            color = SpColor.OnBackground,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() },
        )
        if (state.achievements.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = "Has achievements",
                tint = SpColor.Warning,
                modifier = Modifier.size(24.dp),
            )
        }
    }

    Spacer(Modifier.height(SpSpacing.Small))

    // Badges row: console, verification, region, IGDB rating, community rating
    Row(
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpConsoleChip(
            consoleName = game.consoleName,
            consoleColor = getConsoleColor(game.consoleName),
        )
        VerificationChip(
            verificationStatus = game.verificationStatus,
            verificationTag = game.verificationTag,
        )
        game.region?.takeIf { it.isNotBlank() }?.let { SpChip(text = it) }
    }

    if (game.rating > 0) {
        Spacer(Modifier.height(SpSpacing.Small))
        IgdbRatingStars(rating = game.rating)
    }

    if (state.isScraping) {
        Spacer(Modifier.height(SpSpacing.Small))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = SpColor.Primary,
            )
            Text(
                text = "Scraping metadata\u2026",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
    }

    Spacer(Modifier.height(SpSpacing.XLarge))

    // Action buttons row: Play/Download + Actions menu
    val supportsNetplay = game.consoleId.lowercase() in NETPLAY_SUPPORTED_CONSOLES

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                    .weight(1f)
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
                    .weight(1f)
                    .semantics {
                        contentDescription = if (isBusy) "Downloading ${game.title}"
                        else "Download ${game.title}"
                    },
                isLoading = isBusy,
                enabled = !isBusy,
                menuItems = menuItems,
            )
        }

        GameActionsMenu(
            isFavorite = game.isFavorite,
            isInPlayLater = game.isInPlayLater,
            onToggleFavorite = onToggleFavorite,
            onTogglePlayLater = onTogglePlayLater,
            onAddToCollection = onAddToCollection,
        )
    }

    // Playtime + last played
    if (game.totalPlayTime > 0 || game.lastPlayedAt != null) {
        Spacer(Modifier.height(SpSpacing.Medium))
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AccessTime,
                contentDescription = null,
                tint = SpColor.OnBackgroundTertiary,
                modifier = Modifier.size(16.dp),
            )
            if (game.totalPlayTime > 0) {
                Text(
                    text = formatPlayTime(game.totalPlayTime),
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
                )
            }
            game.lastPlayedAt?.let { timestamp ->
                val relative = formatRelativeTime(timestamp)
                if (relative.isNotEmpty()) {
                    Text(
                        text = "Last played $relative",
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        }
    }

    // BIOS warning chip
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
        SpTitledSection(title = "About", includeTopSpacing = false) {
            Text(
                text = description,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )
        }
        Spacer(Modifier.height(SpSpacing.XLarge))
    }

    // Metadata grid (Developer, Publisher, Released, Genre, Players, Size, Discs)
    MetadataGrid(game = game)
}

@Composable
private fun IgdbRatingStars(rating: Double) {
    val normalized = rating / 10.0
    val starValue = normalized / 2.0
    val fullStars = starValue.toInt()
    val hasHalf = starValue - fullStars >= 0.5
    val emptyStars = 5 - fullStars - if (hasHalf) 1 else 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
        modifier = Modifier.semantics {
            contentDescription = "IGDB rating: ${"%.1f".format(normalized)} out of 10"
        },
    ) {
        repeat(fullStars) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = SpColor.Warning,
                modifier = Modifier.size(16.dp),
            )
        }
        if (hasHalf) {
            Box(modifier = Modifier.size(16.dp)) {
                Icon(
                    imageVector = Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = SpColor.Warning,
                    modifier = Modifier.size(16.dp),
                )
                Box(modifier = Modifier.size(width = 8.dp, height = 16.dp).clipToBounds()) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = SpColor.Warning,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        repeat(emptyStars) {
            Icon(
                imageVector = Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = SpColor.OnBackgroundTertiary,
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.size(SpSpacing.XXSmall))

        Text(
            text = "${"%.1f".format(normalized)}/10",
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundSecondary,
        )
    }
}
