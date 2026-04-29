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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onToggleFavorite: () -> Unit,
    onTogglePlayLater: () -> Unit,
    onAddToCollection: () -> Unit,
    onCreateNetplay: ((String) -> Unit)?,
    onDeleteLocalGame: () -> Unit,
    syncState: GameSyncState?,
    onNavigateToAchievements: () -> Unit = {},
    onAdminScrape: (() -> Unit)? = null,
    onAdminRefreshAchievements: (() -> Unit)? = null,
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
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isGameCached) {
                if (game.playable) {
                    val menuItems = buildList {
                        if (hasSaves && onPlayFromTitleScreen != null) {
                            add(SpSplitButtonMenuItem(
                                label = "Continue from Title Screen",
                                description = "Keep your in-game save, start from the beginning",
                            ) { onPlayFromTitleScreen(gameId) })

                        }
                        if (hasSaves && onPlayFresh != null) {
                            add(SpSplitButtonMenuItem(
                                label = "New Game",
                                description = "Start a separate playthrough from scratch",
                            ) { onPlayFresh(gameId) })
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

            // Status indicators (scraping, syncing, downloading)
            val statusText = when {
                state.isScraping -> "Scraping\u2026"
                syncState != null && !syncState.isTimedOut -> syncState.message
                state.isDownloading -> {
                    val p = state.downloadProgress
                    if (p != null && p.totalDiscs > 1) "Downloading disc ${p.currentDisc}/${p.totalDiscs}\u2026"
                    else "Downloading\u2026"
                }
                else -> null
            }
            if (statusText != null) {
                Column(
                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                    ) {
                        // Show spinner for non-download statuses (scraping, sync).
                        // Downloads already have a spinner in the button.
                        if (!state.isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.65f),
                            )
                        }
                        Text(
                            text = statusText,
                            style = SpTypography.LabelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }

                    // Download progress: bytes + bar / indeterminate stripe.
                    // Only when an active download is reporting progress.
                    val dp = state.downloadProgress
                    if (state.isDownloading && dp != null && dp.state == DownloadState.DOWNLOADING) {
                        SpDownloadProgressBar(
                            progress = dp.progress,
                            bytesDownloaded = dp.bytesDownloaded,
                            totalBytes = dp.totalBytes,
                            onGradient = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
                                text = relative,
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
