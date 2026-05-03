package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.INSTANT_DOWNLOAD_THRESHOLD_BYTES
import com.spela.player.domain.model.NETPLAY_SUPPORTED_CONSOLES
import com.spela.player.presentation.state.GameDetailState
import com.spela.player.presentation.state.GameSyncState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpConsoleChip
import com.spela.player.presentation.ui.components.SpDownloadProgressBar
import com.spela.player.presentation.ui.components.SpRegionChip
import com.spela.player.presentation.ui.components.SpSplitButton
import com.spela.player.presentation.ui.components.SpSplitButtonMenuItem
import com.spela.player.presentation.ui.components.social.formatRelativeTime
import com.spela.player.presentation.ui.feature.library.getConsoleColor
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatBytes
import com.spela.player.util.formatPlayTime

/**
 * Content rendered inside the hero banner's contrast backdrop in
 * portrait mode. Shows game title, badges, ratings, and action
 * buttons.
 *
 * Extracted from `GameDetailScreen` in the #695 refactor — the
 * composable is self-contained enough to own its own file, and the
 * two rating badges below are only used here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameHeroContent(
    gameId: String,
    game: Game,
    state: GameDetailState,
    hasSaves: Boolean,
    missingBiosFiles: List<BiosMissingFile>,
    onPlay: (String) -> Unit,
    onPlayFresh: ((String) -> Unit)?,
    onPlayFromTitleScreen: ((String) -> Unit)? = null,
    onDownloadGame: () -> Unit,
    /**
     * Sub-threshold instant-download flow (#932). Wired by the screen
     * to dispatch GameDetailIntent.DownloadGameAndPlay. Tap on the
     * Play/Resume/Continue button for a small uncached game routes
     * here instead of onDownloadGame, so the user experiences a
     * single-click launch.
     *
     * No default: the wrong fallback would silently drop the
     * auto-launch behaviour without a compile error. Caller must
     * supply an explicit handler.
     */
    onDownloadAndPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayLater: () -> Unit,
    onAddToCollection: () -> Unit,
    onCreateNetplay: ((String) -> Unit)?,
    onDeleteLocalGame: () -> Unit,
    syncState: GameSyncState?,
    onNavigateToAchievements: () -> Unit = {},
    onAdminScrape: (() -> Unit)? = null,
    onAdminRefreshAchievements: (() -> Unit)? = null,
    /**
     * Per-game save-state policy override. The control lives behind a
     * gear icon in this hero action row → Game settings sheet (#855),
     * not inline in the info column where it used to be.
     */
    onSetGameSaveStatePolicy: (com.spela.player.domain.model.SaveStateChoice?) -> Unit = {},
) {
    val supportsNetplay = game.playable && game.consoleId.lowercase() in NETPLAY_SUPPORTED_CONSOLES
    var showGameSettingsSheet by remember { mutableStateOf(false) }

    // #893: hoist the busy state so the size hint can sit outside the
    // FlowRow that hosts the Download button. Keeping the size hint
    // inside a wrapping Column inside the row used to push the button
    // upward — FlowRow's CenterVertically alignment centres the *whole
    // child* (button + size hint) against the row's tallest item, so
    // the button drifted off-axis from the "..." actions menu.
    val isActivelyDownloading = state.downloadProgress?.state == DownloadState.DOWNLOADING
    val isBusy = state.isDownloading || isActivelyDownloading

    // #932: a sub-threshold uncached game shows the cached-style
    // Play/Resume/Continue button. Tap kicks off a silent
    // download-then-launch via the ViewModel. Once we exit the silent
    // window (state.isInstantDownload flips false), we fall back to
    // the regular Download button + progress bar for the rest of the
    // wait so the user knows something is happening.
    val isInstantDownloadCandidate =
        !state.isGameCached &&
            game.fileSize in 1..<INSTANT_DOWNLOAD_THRESHOLD_BYTES
    val showCachedStyleButton = state.isGameCached ||
        (isInstantDownloadCandidate && (!state.isDownloading || state.isInstantDownload))

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
            if (game.igdbCriticsRating > 0) {
                IgdbRatingStars(rating = game.igdbCriticsRating)
            }
            if (game.communityRating > 0) {
                CommunityRatingBadge(
                    averageRating = game.communityRating,
                    ratingCount = game.communityRatingCount,
                )
            }
            if (state.achievements.isNotEmpty()) {
                SpChip(
                    text = "${state.achievementProgress.size} / ${state.achievements.size}",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = SpColor.OnGradientSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    onGradient = true,
                    onClick = onNavigateToAchievements,
                )
            }
        }

        // Action buttons + size hint. Wrapping inner Column keeps the
        // hint visually tied to the Download button while the FlowRow
        // itself only contains same-height button-shaped items so
        // CenterVertically alignment is honest. See #893.
        Column(verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCachedStyleButton) {
                if (game.playable) {
                    val menuItems = buildList {
                        if (hasSaves && onPlayFromTitleScreen != null) {
                            add(SpSplitButtonMenuItem(
                                label = "Continue from Title Screen",
                                description = "Keep your in-game save, start from the beginning",
                            ) { onPlayFromTitleScreen(gameId) })

                        }
                        if (hasSaves && onPlayFresh != null) {
                            // Renamed from "New Game" to disambiguate
                            // from the top-button label which now also
                            // says "New game" when no session exists
                            // (#900). The fresh-playthrough action
                            // wants distinct phrasing — "Start fresh
                            // playthrough" reads as a deliberate
                            // override of the existing save state.
                            add(SpSplitButtonMenuItem(
                                label = "Start fresh playthrough",
                                description = "Keep your existing saves, start a separate playthrough from scratch",
                            ) { onPlayFresh(gameId) })
                        }
                        if (onCreateNetplay != null && supportsNetplay) {
                            add(SpSplitButtonMenuItem("Netplay") { onCreateNetplay(gameId) })
                        }
                        // Delete Download only meaningful when the
                        // game is actually downloaded — skip on the
                        // instant-download silent-Play path (#932).
                        if (state.isGameCached) {
                            add(SpSplitButtonMenuItem("Delete Download") { onDeleteLocalGame() })
                        }
                    }
                    val hasRequiredBiosMissing = missingBiosFiles.any { it.required }
                    val isSyncing = syncState != null
                    // Three-state label per #884 / #900:
                    //   NoSession → "New game" (no save to pick up
                    //     from — the user is starting fresh)
                    //   ResumesFromSaveState → "Resume" (auto-load
                    //     on launch — user lands mid-frame)
                    //   LaunchesFresh → "Continue" (session exists
                    //     but engine starts at title screen, e.g.
                    //     ScummVM or user opted out of save states)
                    // The single-source-of-truth resolver lives in
                    // GameDetailState.playSemantics.
                    val playLabel = when (state.playSemantics) {
                        com.spela.player.domain.model.PlaySemantics.NoSession -> "New game"
                        com.spela.player.domain.model.PlaySemantics.ResumesFromSaveState -> "Resume"
                        com.spela.player.domain.model.PlaySemantics.LaunchesFresh -> "Continue"
                    }
                    // Click target depends on whether the game is
                    // already on disk. Cached → direct play. Uncached
                    // (only reachable here when isInstantDownload-
                    // Candidate is true) → silent download-then-
                    // launch via the ViewModel; the screen
                    // LaunchedEffect will call onPlay once download
                    // succeeds. The ViewModel additionally gates the
                    // auto-launch on its own size check so this
                    // routing can't be inadvertently broken from
                    // elsewhere. #932.
                    val playOnClick: () -> Unit = if (state.isGameCached) {
                        { onPlay(gameId) }
                    } else {
                        onDownloadAndPlay
                    }
                    SpSplitButton(
                        text = playLabel,
                        onClick = playOnClick,
                        modifier = Modifier
                            .autoFocus()
                            .testTag("game_detail_play_button"),
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
                val menuItems = buildList {
                    if (onCreateNetplay != null && supportsNetplay) {
                        add(SpSplitButtonMenuItem("Netplay") { onCreateNetplay(gameId) })
                    }
                }
                SpSplitButton(
                    text = if (isBusy) "Downloading..." else "Download",
                    onClick = onDownloadGame,
                    modifier = Modifier.testTag("game_detail_download_button"),
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
                onAdminScrape = onAdminScrape,
                onAdminRefreshAchievements = onAdminRefreshAchievements,
                isAdminActionLoading = state.isAdminActionLoading,
            )

            // Game settings — currently only the per-game save-state
            // policy override (#855). Lives behind a gear icon, not
            // inline, so the rare-use override doesn't compete with
            // Play / Resume for vertical real-estate. Future per-game
            // policies (shader, core, input remap) can land in the
            // same sheet without re-litigating placement.
            com.spela.player.presentation.ui.components.SpIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Game settings",
                onClick = { showGameSettingsSheet = true },
                onGradient = true,
                modifier = Modifier.testTag("game_detail_settings_button"),
            )

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
                                    tint = SpColor.OnGradientSecondary,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                        )
                    }
                    game.lastPlayedAt?.let { timestamp ->
                        val relative = formatRelativeTime(timestamp)
                        if (relative.isNotEmpty()) {
                            SpChip(
                                text = relative,
                                onGradient = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        tint = SpColor.OnGradientSecondary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
            // Size hint under the action row — answers "is this 5 MB
            // or 5 GB?" before the user commits, without scrolling
            // down to the metadata table. Hidden during the active
            // download because SpDownloadProgressBar already shows
            // the downloaded / total byte counter. Lives outside the
            // FlowRow so its presence doesn't push the Download button
            // off vertical centre with the "..." actions menu (#893).
            if (!state.isGameCached && !isBusy && game.fileSize > 0) {
                Text(
                    text = formatBytes(game.fileSize),
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnGradientSecondary,
                    modifier = Modifier.testTag("game_detail_download_size"),
                )
            }
        }

        // Status indicators (scraping, syncing, downloading) — sibling of
        // the FlowRow above so their layout never interacts with how the
        // FlowRow wraps action items. Visibility is latched on
        // state.isDownloading (not on dp.state) so a single in-flight
        // progress event doesn't flip the indicator on/off and reflow
        // the page each frame (#797).
        // #932: hide download status text + bar during the silent
        // window of an instant-download. After the 750 ms fallback,
        // state.isInstantDownload flips false and the regular UI
        // takes over.
        val showDownloadStatus = state.isDownloading && !state.isInstantDownload
        val statusText = when {
            state.isScraping -> "Scraping…"
            state.isScrapeQueued -> "Scrape queued"
            syncState != null && !syncState.isTimedOut -> syncState.message
            showDownloadStatus -> {
                val p = state.downloadProgress
                if (p != null && p.totalDiscs > 1) "Downloading disc ${p.currentDisc}/${p.totalDiscs}…"
                else "Downloading…"
            }
            else -> null
        }
        if (statusText != null) {
            // fillMaxWidth + widthIn cap the column at 320dp wide and
            // give it a stable measured width every recomposition.
            // Without fillMaxWidth, the column's intrinsic width tracks
            // the widest child (the bar's bytes-downloaded / speed text
            // row), which changes with every progress emission and
            // ripples into the bar's fillMaxWidth — contributing to the
            // #894 reflow-per-frame symptom on top of the bar-height
            // latch fix in SpDownloadProgressBar.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    // Show spinner for non-download statuses (scraping, sync).
                    // Downloads already have a spinner in the button.
                    if (!showDownloadStatus) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = SpColor.OnGradientSecondary,
                        )
                    }
                    Text(
                        text = statusText,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnGradientSecondary,
                    )
                }

                // Latched on isDownloading. The dp object's individual
                // values (progress, bytes) still update continuously —
                // only the show/hide decision is held stable.
                if (showDownloadStatus) {
                    val dp = state.downloadProgress
                    SpDownloadProgressBar(
                        progress = dp?.progress ?: -1f,
                        bytesDownloaded = dp?.bytesDownloaded ?: 0L,
                        totalBytes = dp?.totalBytes ?: -1L,
                        bytesPerSecond = dp?.bytesPerSecond ?: 0L,
                        onGradient = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showGameSettingsSheet) {
        AlertDialog(
            onDismissRequest = { showGameSettingsSheet = false },
            title = { Text("Game settings") },
            text = {
                GameSaveStatePolicyToggle(
                    current = state.gameSaveStatePolicy,
                    onChange = onSetGameSaveStatePolicy,
                )
            },
            confirmButton = {
                TextButton(onClick = { showGameSettingsSheet = false }) {
                    Text("Done")
                }
            },
            modifier = Modifier.testTag("game_detail_settings_sheet"),
        )
    }
}

@Composable
private fun IgdbRatingStars(rating: Double) {
    val normalized = rating / 10.0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
        modifier = Modifier.semantics {
            contentDescription = "IGDB rating: ${"%.1f".format(normalized)} out of 10"
        },
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = SpColor.Warning,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "%.1f".format(normalized),
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
