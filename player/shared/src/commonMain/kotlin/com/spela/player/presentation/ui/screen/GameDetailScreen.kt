package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.state.GameSyncState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import com.spela.player.presentation.ui.feature.collections.CollectionPickerDialog
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.presentation.ui.feature.gamedetail.GameHeroContent
import com.spela.player.presentation.ui.feature.gamedetail.GameInfoContent
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
import com.spela.player.presentation.ui.feature.library.getConsoleGradient
import com.spela.player.presentation.ui.components.GameDetailLayout
import com.spela.player.presentation.ui.components.GameDetailSkeleton
import com.spela.player.presentation.ui.components.onApproachingVisible
import com.spela.player.presentation.ui.components.SpConfirmDialog
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.social.StarRatingRow
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
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
    onPlayFromTitleScreen: ((String) -> Unit)? = null,
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
    onPlaySessionFromTitleScreen: ((gameId: String, sessionId: String) -> Unit)? = null,
    onNavigateToSession: ((sessionId: String) -> Unit)? = null,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    val keyMappingState = keyMappingViewModel?.state?.collectAsState()

    LaunchedEffect(gameId) {
        viewModel.onIntent(GameDetailIntent.LoadGame(gameId))
    }

    // Navigate to play when a session is created from a shared save.
    // Mark the ID consumed immediately after dispatching navigation so a
    // later recomposition with the same state value doesn't re-fire and
    // push a duplicate emulation screen onto the back-stack.
    LaunchedEffect(state.playFromSharedSaveSessionId) {
        val sessionId = state.playFromSharedSaveSessionId ?: return@LaunchedEffect
        onPlaySession?.invoke(gameId, sessionId)
        viewModel.onIntent(GameDetailIntent.ConsumePlayFromSharedSaveNavigation)
    }

    // #932: instant-download flow finished — drive the play handler
    // and clear the flag. No-op if pendingAutoLaunch is false.
    LaunchedEffect(state.pendingAutoLaunch) {
        if (state.pendingAutoLaunch) {
            onPlay(gameId)
            viewModel.onIntent(GameDetailIntent.ConsumeAutoLaunch)
        }
    }

    if (state.isLoading && state.gameDetail == null) {
        GameDetailSkeleton(onBack = onBack)
        return
    }

    val detail = state.gameDetail ?: return
    val game = detail.game
    val isDemoConsole = state.console?.abbreviation == "ADEMO" || state.console?.abbreviation == "DDEMO"

    // Per-console gradient background (same as console screen, darkened).
    // Sourced from the loaded Console's colorTheme so this surface stays
    // in sync with the consoles-list and console-detail hero (#1167).
    val consoleColorTheme = state.console?.colorTheme
    val backgroundColors = remember(consoleColorTheme) {
        val (from, to) = getConsoleGradient(consoleColorTheme)
        listOf(from.darken(0.65f), to.darken(0.65f))
    }

    val focusMemory = rememberFocusMemoryState()
    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
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
                    onPlayFromTitleScreen = onPlayFromTitleScreen,
                    onDownloadGame = { viewModel.onIntent(GameDetailIntent.DownloadGame) },
                    onDownloadAndPlay = { viewModel.onIntent(GameDetailIntent.DownloadGameAndPlay) },
                    onToggleFavorite = { viewModel.onIntent(GameDetailIntent.ToggleFavorite) },
                    onTogglePlayLater = { viewModel.onIntent(GameDetailIntent.TogglePlayLater) },
                    onAddToCollection = { viewModel.onIntent(GameDetailIntent.ShowAddToCollectionDialog) },
                    onCreateNetplay = onCreateNetplay,
                    onDeleteLocalGame = { viewModel.onIntent(GameDetailIntent.ShowDeleteDownloadDialog) },
                    syncState = syncState,
                    onNavigateToAchievements = { onNavigateToAchievements?.invoke(gameId) },
                    onAdminScrape = if (state.isAdmin) {{ viewModel.onIntent(GameDetailIntent.AdminScrapeGame) }} else null,
                    onAdminRefreshAchievements = if (state.isAdmin) {{ viewModel.onIntent(GameDetailIntent.AdminRefreshAchievements) }} else null,
                    onSetGameSaveStatePolicy = { choice ->
                        viewModel.onIntent(GameDetailIntent.SetGameSaveStatePolicy(choice))
                    },
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
                // Below-the-cover left column (landscape only — portrait
                // renders these inline further down the page). Mirrors
                // the web layout (#1099): rating + Time to Beat stacked
                // in a compact left-side column, beside the game
                // description / metadata on the right. The Column gives
                // the two sections explicit vertical breathing room;
                // SpTitledSection itself has no outer padding.
                if (!isPortrait) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
                    ) {
                        SpTitledSection(
                            title = "Your Rating",
                            icon = Icons.Filled.Star,
                        ) {
                            StarRatingRow(
                                currentRating = state.myRating,
                                averageRating = state.ratingSummary?.averageRating ?: game.communityRating,
                                ratingCount = state.ratingSummary?.totalRatings ?: game.communityRatingCount,
                                onRate = { rating ->
                                    viewModel.onIntent(GameDetailIntent.RateGame(rating))
                                },
                            )
                        }
                        if (!isDemoConsole &&
                            (game.timeToBeatHastily > 0 ||
                                game.timeToBeatNormally > 0 ||
                                game.timeToBeatCompletely > 0)
                        ) {
                            TimeToBeatSection(game = game)
                        }
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
                    onPlayFromTitleScreen = onPlayFromTitleScreen,
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
                        onContinueSessionFromTitleScreen = { session ->
                            onPlaySessionFromTitleScreen?.invoke(game.id, session.id)
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
                        onCloneSession = { sessionId, name, saveId ->
                            viewModel.onIntent(GameDetailIntent.CloneSession(sessionId, name, saveId))
                        },
                        // #885 — gate Share session… on the same
                        // PlaySemantics resolver as the hero label
                        // (#884). Sharing only makes sense when save
                        // state will actually transfer; ScummVM /
                        // demo cores / user-disabled consoles never
                        // see the menu item.
                        onShareSession = { session ->
                            viewModel.onIntent(GameDetailIntent.ShowShareSessionDialog(session.id))
                        },
                        shareEnabled = state.playSemantics ==
                            com.spela.player.domain.model.PlaySemantics.ResumesFromSaveState,
                        onSessionSelected = onNavigateToSession?.let { nav ->
                            { session -> nav(session.id) }
                        },
                    )
                }

                // Time to Beat was here as a top-level section; it now
                // lives next to "Your Rating" (in coverExtra for
                // landscape, inline below for portrait) so the pairing
                // matches the web layout (#1099).

                // Community Stats (Play Activity)
                GameCommunityStatsSection(
                    stats = state.gameStats,
                    isLoading = state.isLoadingStats,
                    onPlayerClicked = onNavigateToUser,
                )

                // Your Rating + Time to Beat (portrait only — landscape
                // shows them under cover art in coverExtra). Web pairs
                // these two compactly in a left-side column; we mirror
                // that here.
                if (isPortraitScreen) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StarRatingRow(
                            currentRating = state.myRating,
                            averageRating = state.ratingSummary?.averageRating ?: game.communityRating,
                            ratingCount = state.ratingSummary?.totalRatings ?: game.communityRatingCount,
                            onRate = { rating ->
                                viewModel.onIntent(GameDetailIntent.RateGame(rating))
                            },
                        )
                    }
                    if (!isDemoConsole &&
                        (game.timeToBeatHastily > 0 ||
                            game.timeToBeatNormally > 0 ||
                            game.timeToBeatCompletely > 0)
                    ) {
                        TimeToBeatSection(game = game)
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

                // 3b. Similar Games — lazy-fetched. The placeholder Box
                // is always laid out at this position; its
                // onApproachingVisible callback fires the network
                // request the first time the user scrolls (or is
                // about to scroll) the section into view. The
                // SimilarGamesSection itself only renders once data
                // arrives. This keeps /api/games/{id}/similar off the
                // game-detail initial-load hot path entirely — most
                // game-detail visits never reach this section.
                Box(
                    modifier = Modifier.fillMaxWidth().onApproachingVisible {
                        viewModel.requestSimilarGames()
                    },
                ) {
                    if (state.similarGames.isNotEmpty()) {
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

        // #885 — Share session dialog. Visible when the user picked
        // "Share session…" from a session row's overflow menu.
        state.shareSessionDialogSourceId?.let { sourceId ->
            val sourceSession = state.sessions.firstOrNull { it.id == sourceId }
            com.spela.player.presentation.ui.feature.gamedetail.ShareSessionDialog(
                gameTitle = game.title,
                sourceSessionName = sourceSession?.name ?: "this session",
                isSubmitting = state.isCreatingSharedSession,
                onSubmit = { name, description ->
                    viewModel.onIntent(
                        GameDetailIntent.CreateSharedSessionFromSession(
                            sourceSessionId = sourceId,
                            name = name,
                            description = description,
                        )
                    )
                },
                onDismiss = { viewModel.onIntent(GameDetailIntent.DismissShareSessionDialog) },
            )
        }

        // #885 — after a successful create, navigate the user into
        // the new shared session's detail screen so they can invite
        // members. Consume the navigation so re-recompositions don't
        // re-fire it.
        state.shareSessionCreatedId?.let { newSharedSessionId ->
            androidx.compose.runtime.LaunchedEffect(newSharedSessionId) {
                onNavigateToSharedSession?.invoke(newSharedSessionId)
                viewModel.onIntent(GameDetailIntent.ConsumeShareSessionCreatedNavigation)
            }
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
}
