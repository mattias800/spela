package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.GameVariant
import com.spela.player.domain.model.NETPLAY_SUPPORTED_CONSOLES
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.state.GameDetailState
import com.spela.player.presentation.state.GameSyncState
import com.spela.player.presentation.ui.components.LocalAnimationsEnabled
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.History
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
import com.spela.player.presentation.ui.feature.gamedetail.DeveloperGamesSection
import com.spela.player.presentation.ui.feature.gamedetail.GameSharedSessionsSection
import com.spela.player.presentation.ui.feature.gamedetail.SeriesFranchiseSection
import com.spela.player.presentation.ui.feature.gamedetail.GameReviewsSection
import com.spela.player.presentation.ui.feature.gamedetail.SessionsSection
import com.spela.player.presentation.ui.feature.gamedetail.TimeToBeatSection
import com.spela.player.presentation.ui.feature.gamedetail.ScreenshotsSection
import com.spela.player.presentation.ui.feature.gamedetail.SimilarGamesSection
import com.spela.player.presentation.ui.feature.library.darken
import com.spela.player.presentation.ui.feature.library.getConsoleColor
import com.spela.player.presentation.ui.feature.library.getConsoleGradient
import com.spela.player.presentation.ui.components.GameDetailLayout
import com.spela.player.presentation.ui.components.GameDetailSkeleton
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
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
    onNavigateToSharedSession: ((sharedSessionId: String) -> Unit)? = null,
    onNavigateToGame: ((gameId: String) -> Unit)? = null,
    onNavigateToUser: ((userId: String) -> Unit)? = null,
    onNavigateToSeries: ((seriesId: String, seriesName: String) -> Unit)? = null,
    onNavigateToFranchise: ((franchiseId: String, franchiseName: String) -> Unit)? = null,
    syncState: GameSyncState? = null,
    onPlayWithLocalSave: () -> Unit = {},
    onCancelLaunch: () -> Unit = {},
    onPlaySession: ((gameId: String, sessionId: String) -> Unit)? = null,
    onNavigateToSession: ((sessionId: String) -> Unit)? = null,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    val keyMappingState = keyMappingViewModel?.state?.collectAsState()

    LaunchedEffect(gameId) {
        viewModel.onIntent(GameDetailIntent.LoadGame(gameId))
    }

    // Navigate to play when a session is created from a shared save
    LaunchedEffect(state.playFromSharedSaveSessionId) {
        val sessionId = state.playFromSharedSaveSessionId ?: return@LaunchedEffect
        onPlaySession?.invoke(gameId, sessionId)
    }

    if (state.isLoading && state.gameDetail == null) {
        GameDetailSkeleton(onBack = onBack)
        return
    }

    val detail = state.gameDetail ?: return
    val game = detail.game

    // Per-console gradient background (same as console screen, darkened)
    val backgroundColors = remember(game.consoleId) {
        val (from, to) = getConsoleGradient(game.consoleId, null)
        listOf(from.darken(0.65f), to.darken(0.65f))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isPortraitScreen = maxWidth <= maxHeight
        GameDetailLayout(
            topBar = {
                SpTopBar(
                    title = "",
                    showBack = true,
                    onBack = onBack,
                    onGradient = true,
                )
            },
            backgroundColors = backgroundColors,
            coverArt = { modifier, isPortrait ->
                // Read coverUrl from state delegate (not snapshot) so
                // the LazyColumn item recomposes when it changes after scraping.
                SpCoverArt(
                    imageUrl = state.gameDetail?.game?.coverUrl,
                    contentDescription = "${game.title} cover art",
                    modifier = modifier,
                    aspectRatio = null,
                )
            },
            coverExtra = { isPortrait ->
                // User rating below the cover (landscape only; portrait shows it inline)
                if (!isPortrait) {
                    SpTitledSection(
                        title = "Your Rating",
                        icon = Icons.Filled.Star,
                        includeTopSpacing = false,
                    ) {
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
            },
            sections = {
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
                        hasSaves = state.sessions.isNotEmpty(),
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
                        syncState = syncState,
                        onPlayWithLocalSave = onPlayWithLocalSave,
                        onCancelLaunch = onCancelLaunch,
                        onNavigateToGame = onNavigateToGame,
                    )

                    // Series & Franchise links
                    if (state.gameSeries.isNotEmpty() || state.gameFranchises.isNotEmpty()) {
                        SeriesFranchiseSection(
                            series = state.gameSeries,
                            franchises = state.gameFranchises,
                            onSeriesSelected = onNavigateToSeries,
                            onFranchiseSelected = onNavigateToFranchise,
                        )
                    }
                }

            },
            fullWidthSections = {
                // Section ordering matches web UI:

                // 1. Sessions (top of cards, matching web UI)
                Column(
                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                ) {
                    SessionsSection(
                        sessions = state.sessions,
                        isLoading = state.isLoadingSessions,
                        onContinueSession = { session ->
                            onPlaySession?.invoke(game.id, session.id)
                        },
                        onCreateSession = { name ->
                            viewModel.onIntent(GameDetailIntent.CreateSession(game.id, name))
                        },
                        onRenameSession = { sessionId, name ->
                            viewModel.onIntent(GameDetailIntent.RenameSession(sessionId, name))
                        },
                        onDeleteSession = { sessionId ->
                            viewModel.onIntent(GameDetailIntent.DeleteSession(sessionId))
                        },
                        onDuplicateSession = { sessionId ->
                            viewModel.onIntent(GameDetailIntent.DuplicateSession(sessionId))
                        },
                        onSessionSelected = onNavigateToSession?.let { nav ->
                            { session -> nav(session.id) }
                        },
                    )
                }

                // 2. Time to Beat
                if (game.timeToBeatHastily > 0 || game.timeToBeatNormally > 0 || game.timeToBeatCompletely > 0) {
                    Column(
                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                    ) {
                        TimeToBeatSection(game = game)
                    }
                }

                // 3. Community Stats (Play Activity)
                Column(
                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                ) {
                    GameCommunityStatsSection(
                        stats = state.gameStats,
                        isLoading = state.isLoadingStats,
                        onPlayerClicked = onNavigateToUser,
                    )
                }

                // Your Rating (portrait only — landscape shows it under cover art)
                if (isPortraitScreen) {
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

                // 2. Reviews
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

                // 3. Screenshots
                ScreenshotsSection(detail.screenshots)

                // 3b. Similar Games
                if (state.similarGames.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                    ) {
                        SimilarGamesSection(
                            games = state.similarGames,
                            onGameSelected = { gameId ->
                                onNavigateToGame?.invoke(gameId)
                            },
                        )
                    }
                }

                // 3c. More from Developer
                if (state.developerGames.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                    ) {
                        DeveloperGamesSection(
                            games = state.developerGames,
                            developerName = state.developerName,
                            onGameSelected = { gameId ->
                                onNavigateToGame?.invoke(gameId)
                            },
                        )
                    }
                }

                // Sections 4-10 are only shown for playable games (games with
                // native libretro core support). Non-playable games (external
                // emulator only) show download, metadata, community stats, reviews,
                // screenshots, and similar games — but not saves, controls,
                // challenges, shared sessions, or achievements.
                if (game.playable) {
                    // 4. Achievements
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

                    // 5. Community Shares
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
                            onPlayFromSave = if (onPlaySession != null) { saveId ->
                                viewModel.onIntent(GameDetailIntent.PlayFromSharedSave(saveId))
                            } else null,
                        )
                    }

                    // 8. Game Controls - app-specific
                    if (keyMappingViewModel != null && keyMappingState != null) {
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

                    // 9. Challenges
                    if (onNavigateToChallenges != null) {
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

                    // 10. Active Shared Sessions
                    if (onNavigateToSharedSession != null) {
                        Column(
                            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                        ) {
                            GameSharedSessionsSection(
                                sharedSessions = state.gameSharedSessions,
                                isLoading = state.isLoadingSharedSessions,
                                onSharedSessionClick = { sharedSessionId -> onNavigateToSharedSession(sharedSessionId) },
                            )
                        }
                    }
                }

                // Bottom spacing
                Spacer(Modifier.height(SpSpacing.XXLarge))
            },
        )

        // Save sync timeout dialog
        if (syncState?.isTimedOut == true) {
            com.spela.player.presentation.ui.components.SpDialog(
                title = "Could not sync save",
                onDismiss = onCancelLaunch,
                onConfirm = onPlayWithLocalSave,
                confirmText = "Play with local save",
                dismissText = "Cancel",
            ) {
                androidx.compose.material3.Text(
                    text = "The server could not be reached. You can play using your local save, or cancel.",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                )
            }
        }

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
                saveStates = emptyList(),
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
    syncState: GameSyncState? = null,
    onPlayWithLocalSave: () -> Unit = {},
    onCancelLaunch: () -> Unit = {},
    onNavigateToGame: ((String) -> Unit)? = null,
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
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SpConsoleChip(
            consoleName = game.consoleName,
            consoleColor = getConsoleColor(game.consoleName),
            onGradient = true,
        )
        VerificationChip(
            verificationStatus = game.verificationStatus,
            verificationTag = game.verificationTag,
        )
        game.region?.takeIf { it.isNotBlank() }?.let { region ->
            val flag = getRegionFlag(region)
            SpChip(text = if (flag != null) "$flag $region" else region, onGradient = true)
        }
        if (game.rating > 0) {
            IgdbRatingStars(rating = game.rating)
        }
        if (game.averageRating > 0) {
            CommunityRatingBadge(
                averageRating = game.averageRating,
                ratingCount = game.ratingCount,
            )
        }
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
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = "Scraping metadata\u2026",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
    }

    Spacer(Modifier.height(SpSpacing.XLarge))

    // Action buttons row: Play/Download + Actions menu + playtime chips
    val supportsNetplay = game.playable && game.consoleId.lowercase() in NETPLAY_SUPPORTED_CONSOLES

    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isGameCached) {
            if (game.playable) {
                // Playable game: show Play/Resume with split menu
                val menuItems = buildList {
                    if (hasSaves && onPlayFresh != null) {
                        add(SpSplitButtonMenuItem("New Game") { onPlayFresh(gameId) })
                    }
                    if (onCreateNetplay != null && supportsNetplay) {
                        add(SpSplitButtonMenuItem("Netplay") { onCreateNetplay(gameId) })
                    }
                    add(SpSplitButtonMenuItem("Delete Download") { onDeleteLocalGame() })
                }

                val hasRequiredBiosMissing = missingBiosFiles.any { it.required }
                val isSyncing = syncState != null
                val shadowShape = RoundedCornerShape(SpSpacing.RadiusLarge)
                val shadowColor = SpColor.Primary.copy(alpha = 0.20f)
                SpSplitButton(
                    text = if (hasSaves) "Resume" else "Play",
                    onClick = { onPlay(gameId) },
                    enabled = !hasRequiredBiosMissing && !isSyncing,
                    isLoading = false,
                    modifier = Modifier
                        .shadow(10.dp, shadowShape, ambientColor = shadowColor, spotColor = shadowColor)
                        .semantics {
                            contentDescription = when {
                                hasRequiredBiosMissing -> "Play disabled, BIOS required"
                                isSyncing -> "Play disabled, syncing"
                                hasSaves -> "Resume ${game.title}"
                                else -> "Play ${game.title}"
                            }
                        },
                    menuItems = menuItems,
                    onGradient = true,
                )
            } else {
                // Non-playable game (downloaded): show Delete Download button only
                SpButton(
                    text = "Delete Download",
                    onClick = onDeleteLocalGame,
                    style = SpButtonStyle.Ghost,
                    onGradient = true,
                )
            }
        } else {
            val isActivelyDownloading = state.downloadProgress?.state == DownloadState.DOWNLOADING
            val isBusy = state.isDownloading || isActivelyDownloading

            val menuItems = buildList {
                if (onCreateNetplay != null && supportsNetplay) {
                    add(SpSplitButtonMenuItem("Netplay") { onCreateNetplay(gameId) })
                }
            }

            val shadowShape = RoundedCornerShape(SpSpacing.RadiusLarge)
            val shadowColor = SpColor.Primary.copy(alpha = 0.20f)
            SpSplitButton(
                text = if (isBusy) "Downloading..." else "Download",
                onClick = onDownloadGame,
                modifier = Modifier
                    .shadow(10.dp, shadowShape, ambientColor = shadowColor, spotColor = shadowColor)
                    .semantics {
                        contentDescription = if (isBusy) "Downloading ${game.title}"
                        else "Download ${game.title}"
                    },
                isLoading = isBusy,
                enabled = !isBusy,
                menuItems = menuItems,
                onGradient = true,
            )
        }

        GameActionsMenu(
            isFavorite = game.isFavorite,
            isInPlayLater = game.isInPlayLater,
            onToggleFavorite = onToggleFavorite,
            onTogglePlayLater = onTogglePlayLater,
            onAddToCollection = onAddToCollection,
            onGradient = true,
        )

        // "External Emulator" indicator for non-playable games
        if (!game.playable) {
            SpChip(
                text = "External Emulator",
                onGradient = true,
            )
        }

        // Playtime + last played as highlighted chips (inline with buttons)
        if (game.totalPlayTime > 0) {
            SpChip(
                text = formatPlayTime(game.totalPlayTime),
                onGradient = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }
        game.lastPlayedAt?.let { timestamp ->
            val relative = formatRelativeTime(timestamp)
            if (relative.isNotEmpty()) {
                SpChip(
                    text = "Last played $relative",
                    onGradient = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
        }
    }

    // Sync status row (shown while pre-launch or post-exit sync is in progress)
    syncState?.takeIf { !it.isTimedOut }?.let { sync ->
        Spacer(Modifier.height(SpSpacing.Small))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            if (LocalAnimationsEnabled.current) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            Text(
                text = sync.message,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
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
                    onGradient = true,
                )
            }
        }
    }

    Spacer(Modifier.height(SpSpacing.XLarge))

    // Description (plain text, matching web UI)
    game.description?.let { description ->
        Text(
            text = description,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
        Spacer(Modifier.height(SpSpacing.XLarge))
    }

    // Metadata grid (Developer, Publisher, Released, Genre, Players, Size, Discs)
    MetadataGrid(game = game, onGradient = true)

    // Variants section
    if (detail.variants.isNotEmpty()) {
        Spacer(Modifier.height(SpSpacing.XLarge))
        VariantsSection(
            variants = detail.variants,
            onVariantSelected = onNavigateToGame,
        )
    }
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
                modifier = Modifier.size(20.dp),
            )
        }
        if (hasHalf) {
            Box(modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = SpColor.Warning,
                    modifier = Modifier.size(20.dp),
                )
                Box(modifier = Modifier.size(width = 10.dp, height = 20.dp).clipToBounds()) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = SpColor.Warning,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        repeat(emptyStars) {
            Icon(
                imageVector = Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = SpColor.OnBackgroundTertiary,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.size(SpSpacing.XXSmall))

        Text(
            text = "${"%.1f".format(normalized)}/10",
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackgroundSecondary,
        )
    }
}

@Composable
private fun CommunityRatingBadge(averageRating: Double, ratingCount: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
        modifier = Modifier.semantics {
            contentDescription = "Community rating: ${"%.1f".format(averageRating)} from $ratingCount ratings"
        },
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = SpColor.Warning,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "%.1f".format(averageRating),
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackgroundTertiary,
        )
        Text(
            text = "($ratingCount)",
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}

private val regionFlags = mapOf(
    "USA" to "\uD83C\uDDFA\uD83C\uDDF8",
    "Japan" to "\uD83C\uDDEF\uD83C\uDDF5",
    "Europe" to "\uD83C\uDDEA\uD83C\uDDFA",
    "World" to "\uD83C\uDF0D",
    "Korea" to "\uD83C\uDDF0\uD83C\uDDF7",
    "Brazil" to "\uD83C\uDDE7\uD83C\uDDF7",
    "France" to "\uD83C\uDDEB\uD83C\uDDF7",
    "Germany" to "\uD83C\uDDE9\uD83C\uDDEA",
    "Spain" to "\uD83C\uDDEA\uD83C\uDDF8",
    "Italy" to "\uD83C\uDDEE\uD83C\uDDF9",
    "Australia" to "\uD83C\uDDE6\uD83C\uDDFA",
    "China" to "\uD83C\uDDE8\uD83C\uDDF3",
    "Canada" to "\uD83C\uDDE8\uD83C\uDDE6",
    "UK" to "\uD83C\uDDEC\uD83C\uDDE7",
    "Sweden" to "\uD83C\uDDF8\uD83C\uDDEA",
    "Netherlands" to "\uD83C\uDDF3\uD83C\uDDF1",
    "Russia" to "\uD83C\uDDF7\uD83C\uDDFA",
    "Taiwan" to "\uD83C\uDDF9\uD83C\uDDFC",
    "Asia" to "\uD83C\uDF0F",
)

private fun getRegionFlag(region: String): String? =
    regionFlags.entries.firstOrNull { region.contains(it.key, ignoreCase = true) }?.value

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantsSection(
    variants: List<GameVariant>,
    onVariantSelected: ((String) -> Unit)?,
) {
    SpTitledSection(
        title = "Versions",
        includeTopSpacing = false,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            variants.forEach { variant ->
                SpCard(
                    onClick = { onVariantSelected?.invoke(variant.id) },
                    onGradient = true,
                    cornerRadius = SpSpacing.RadiusMedium,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(SpSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                    ) {
                        Text(
                            text = variant.title,
                            style = SpTypography.TitleSmall,
                            color = SpColor.OnCard,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                            verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                        ) {
                            variant.region?.takeIf { it.isNotBlank() }?.let { region ->
                                val flag = getRegionFlag(region)
                                SpChip(
                                    text = if (flag != null) "$flag $region" else region,
                                    onGradient = true,
                                )
                            }
                            variant.revision?.takeIf { it.isNotBlank() }?.let { revision ->
                                SpChip(text = revision, onGradient = true)
                            }
                            if (variant.fileSize > 0) {
                                SpChip(
                                    text = formatVariantFileSize(variant.fileSize),
                                    onGradient = true,
                                )
                            }
                            variant.verificationStatus?.takeIf { it.isNotBlank() }?.let { status ->
                                SpChip(text = status, onGradient = true)
                            }
                            if (variant.isPreRelease) {
                                SpChip(text = "Pre-release", onGradient = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatVariantFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
