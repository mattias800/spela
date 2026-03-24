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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.GameVariant
import com.spela.player.domain.model.ParentGame
import com.spela.player.domain.model.RomHackGame
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
import com.spela.player.presentation.ui.components.SpRegionChip
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
    onNavigateToDeveloper: ((name: String) -> Unit)? = null,
    onNavigateToPublisher: ((name: String) -> Unit)? = null,
    onNavigateToAchievements: ((gameId: String) -> Unit)? = null,
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
    val isDemoConsole = state.console?.abbreviation == "ADEMO" || state.console?.abbreviation == "DDEMO"

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
            heroUrl = game.heroUrl ?: detail.screenshots.firstOrNull(),
            heroContent = {
                GameHeroContent(
                    gameId = gameId,
                    game = game,
                    state = state,
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
                    syncState = syncState,
                    onNavigateToAchievements = { onNavigateToAchievements?.invoke(gameId) },
                )
            },
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
                GameInfoContent(
                    gameId = gameId,
                    game = game,
                    detail = detail,
                    state = state,
                    isPortrait = isPortraitScreen,
                    hasSaves = state.sessions.isNotEmpty(),
                    missingBiosFiles = state.missingBiosFiles,
                    isDemoConsole = isDemoConsole,
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
                    onNavigateToDeveloper = onNavigateToDeveloper,
                    onNavigateToPublisher = onNavigateToPublisher,
                    onNavigateToAchievements = if (state.achievements.isNotEmpty()) {
                        { onNavigateToAchievements?.invoke(gameId) }
                    } else null,
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
            },
            fullWidthSections = {
                // Section ordering matches web UI:

                // 1. Sessions (top of cards, matching web UI)
                // Hidden for demo consoles (no save state support)
                if (!isDemoConsole) {
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

                // 2. Time to Beat (hidden for demo consoles)
                if (!isDemoConsole && (game.timeToBeatHastily > 0 || game.timeToBeatNormally > 0 || game.timeToBeatCompletely > 0)) {
                    TimeToBeatSection(game = game)
                }

                // 3. Community Stats (Play Activity)
                GameCommunityStatsSection(
                    stats = state.gameStats,
                    isLoading = state.isLoadingStats,
                    onPlayerClicked = onNavigateToUser,
                )

                // Your Rating (portrait only — landscape shows it under cover art)
                if (isPortraitScreen) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
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

                // 2. Reviews
                GameReviewsSection(
                    reviews = state.reviews,
                    reviewsTotal = state.reviewsTotal,
                    reviewsPage = state.reviewsPage,
                    isLoading = state.isLoadingReviews,
                    onLoadMore = {
                        viewModel.onIntent(GameDetailIntent.LoadMoreReviews(gameId))
                    },
                )

                // 3. Screenshots
                ScreenshotsSection(detail.screenshots)

                // 3b. Similar Games
                if (state.similarGames.isNotEmpty()) {
                    SimilarGamesSection(
                        games = state.similarGames,
                        onGameSelected = { gameId ->
                            onNavigateToGame?.invoke(gameId)
                        },
                    )
                }

                // 3c. More from Developer
                if (state.developerGames.isNotEmpty()) {
                    DeveloperGamesSection(
                        games = state.developerGames,
                        developerName = state.developerName,
                        onGameSelected = { gameId ->
                            onNavigateToGame?.invoke(gameId)
                        },
                    )
                }

                // Sections 4-10 are only shown for playable games (games with
                // native libretro core support). Non-playable games (external
                // emulator only) show download, metadata, community stats, reviews,
                // screenshots, and similar games — but not saves, controls,
                // challenges, shared sessions, or achievements.
                if (game.playable) {
                    // 4. Achievements — now shown on a dedicated sub-screen

                    // 5. Community Shares (hidden for demo consoles — save-based)
                    if (!isDemoConsole) {
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

                    // 9. Challenges (hidden for demo consoles)
                    if (!isDemoConsole && onNavigateToChallenges != null) {
                        ChallengesSection(
                            gameTitle = game.title,
                            onViewAll = { onNavigateToChallenges(gameId, game.title) },
                            onCreateChallenge = {
                                viewModel.onIntent(GameDetailIntent.ShowCreateChallengeDialog)
                            },
                        )
                    }

                    // 10. Active Shared Sessions (hidden for demo consoles)
                    if (!isDemoConsole && onNavigateToSharedSession != null) {
                        GameSharedSessionsSection(
                            sharedSessions = state.gameSharedSessions,
                            isLoading = state.isLoadingSharedSessions,
                            onSharedSessionClick = { sharedSessionId -> onNavigateToSharedSession(sharedSessionId) },
                        )
                    }
                }

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
    isDemoConsole: Boolean = false,
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
    onNavigateToDeveloper: ((name: String) -> Unit)? = null,
    onNavigateToPublisher: ((name: String) -> Unit)? = null,
    onNavigateToAchievements: (() -> Unit)? = null,
) {
    // Title, badges, and action buttons are in the hero banner (GameHeroContent)

    // Sync status row (shown while pre-launch or post-exit sync is in progress)
    syncState?.takeIf { !it.isTimedOut }?.let { sync ->
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


    // Description (plain text, matching web UI)
    game.description?.let { description ->
        Text(
            text = description,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
    }

    // Metadata grid (Developer, Publisher, Released, Genre, Players, Achievements, Size, Discs)
    MetadataGrid(
        game = game,
        onGradient = true,
        isDemoConsole = isDemoConsole,
        achievementTotal = state.achievements.size,
        achievementUnlocked = state.achievementProgress.size,
        onDeveloperClick = onNavigateToDeveloper,
        onPublisherClick = onNavigateToPublisher,
        onAchievementsClick = onNavigateToAchievements,
    )

    // Variants section -- split into Versions (non-hack) and ROM Hacks (hack-tagged)
    val versionVariants = detail.variants.filter { variant ->
        variant.tags?.split(",")?.map { it.trim().lowercase() }?.contains("hack") != true
    }
    val hackVariants = detail.variants.filter { variant ->
        variant.tags?.split(",")?.map { it.trim().lowercase() }?.contains("hack") == true
    }

    if (versionVariants.isNotEmpty()) {
        Spacer(Modifier.height(SpSpacing.Default))
        VariantsSection(
            title = "Versions",
            variants = versionVariants,
            onVariantSelected = onNavigateToGame,
        )
    }

    if (hackVariants.isNotEmpty()) {
        Spacer(Modifier.height(SpSpacing.Default))
        VariantsSection(
            title = "ROM Hacks",
            variants = hackVariants,
            onVariantSelected = onNavigateToGame,
        )
    }

    // "Based on" section for standalone ROM hacks
    detail.parentGame?.let { parent ->
        Spacer(Modifier.height(SpSpacing.Default))
        BasedOnSection(
            parentGame = parent,
            onNavigateToGame = onNavigateToGame,
        )
    }

    // Standalone ROM Hacks section
    if (detail.romHacks.isNotEmpty()) {
        Spacer(Modifier.height(SpSpacing.Default))
        RomHacksSection(
            romHacks = detail.romHacks,
            onNavigateToGame = onNavigateToGame,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantsSection(
    title: String,
    variants: List<GameVariant>,
    onVariantSelected: ((String) -> Unit)?,
) {
    SpTitledSection(
        title = title,
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
                                SpRegionChip(region = region, onGradient = true)
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
                            VerificationChip(
                                verificationStatus = variant.verificationStatus,
                            )
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

@Composable
private fun BasedOnSection(
    parentGame: ParentGame,
    onNavigateToGame: ((String) -> Unit)?,
) {
    SpTitledSection(
        title = "Based on",
    ) {
        SpCard(
            onClick = { onNavigateToGame?.invoke(parentGame.id) },
            onGradient = true,
            cornerRadius = SpSpacing.RadiusMedium,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(SpSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpCoverArt(
                    imageUrl = parentGame.coverUrl,
                    contentDescription = "${parentGame.title} cover art",
                    modifier = Modifier.size(width = SpSpacing.CoverSmallWidth, height = SpSpacing.CoverSmallHeight),
                )
                Text(
                    text = parentGame.title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RomHacksSection(
    romHacks: List<RomHackGame>,
    onNavigateToGame: ((String) -> Unit)?,
) {
    SpTitledSection(
        title = "ROM Hacks",
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            romHacks.forEach { hack ->
                SpCard(
                    onClick = { onNavigateToGame?.invoke(hack.id) },
                    onGradient = true,
                    cornerRadius = SpSpacing.RadiusMedium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(SpSpacing.Medium),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SpCoverArt(
                            imageUrl = hack.coverUrl,
                            contentDescription = "${hack.title} cover art",
                            modifier = Modifier.size(width = SpSpacing.CoverSmallWidth, height = SpSpacing.CoverSmallHeight),
                        )
                        Text(
                            text = hack.title,
                            style = SpTypography.TitleSmall,
                            color = SpColor.OnCard,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Content rendered inside the hero banner's contrast backdrop in portrait mode.
 * Shows game title, badges, ratings, and action buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameHeroContent(
    gameId: String,
    game: Game,
    state: GameDetailState,
    hasSaves: Boolean,
    missingBiosFiles: List<BiosMissingFile>,
    onPlay: (String) -> Unit,
    onPlayFresh: ((String) -> Unit)?,
    onDownloadGame: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayLater: () -> Unit,
    onAddToCollection: () -> Unit,
    onCreateNetplay: ((String) -> Unit)?,
    onDeleteLocalGame: () -> Unit,
    syncState: GameSyncState?,
    onNavigateToAchievements: () -> Unit = {},
) {
    val supportsNetplay = game.playable && game.consoleId.lowercase() in NETPLAY_SUPPORTED_CONSOLES

    Column(
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        // Title
        Text(
            text = game.title,
            style = SpTypography.DisplaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Badges: console, region, verification, ratings
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SpConsoleChip(
                consoleName = game.consoleName,
                consoleColor = getConsoleColor(game.consoleName),
                onGradient = true,
            )
            game.region?.takeIf { it.isNotBlank() }?.let { region ->
                SpRegionChip(region = region, onGradient = true)
            }
            VerificationChip(
                verificationStatus = game.verificationStatus,
                verificationTag = game.verificationTag,
            )
            if (game.rating > 0) {
                IgdbRatingStars(rating = game.rating)
            }
            if (game.averageRating > 0) {
                CommunityRatingBadge(
                    averageRating = game.averageRating,
                    ratingCount = game.ratingCount,
                )
            }
            if (state.achievements.isNotEmpty()) {
                SpChip(
                    text = "${state.achievementProgress.size} / ${state.achievements.size}",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    onGradient = true,
                    onClick = onNavigateToAchievements,
                )
            }
        }

        // Action buttons: Play/Download + Actions menu + playtime
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isGameCached) {
                if (game.playable) {
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
                    SpSplitButton(
                        text = if (hasSaves) "Resume" else "Play",
                        onClick = { onPlay(gameId) },
                        enabled = !hasRequiredBiosMissing && !isSyncing,
                        isLoading = false,
                        menuItems = menuItems,
                        onGradient = true,
                    )
                } else {
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
                SpSplitButton(
                    text = if (isBusy) "Downloading..." else "Download",
                    onClick = onDownloadGame,
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

            // Scraping indicator
            if (state.isScraping) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Text(
                        text = "Scraping\u2026",
                        style = SpTypography.LabelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }

            // Playtime + last played grouped so they wrap together
            if (game.totalPlayTime > 0 || game.lastPlayedAt != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
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
            }
        }
    }
}
