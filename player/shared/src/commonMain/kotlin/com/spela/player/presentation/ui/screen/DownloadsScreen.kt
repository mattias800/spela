package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.DownloadFailureReason
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.DownloadedGame
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpDownloadProgressBar
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpScreenContentList
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenHeading
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.sectionPillClearance
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatBytes
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.viewmodel.DownloadsIntent
import com.spela.player.presentation.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit = {},
    onGameClick: (String) -> Unit = {},
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(DownloadsIntent.LoadDownloads)
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    val focusMemory = rememberFocusMemoryState()

    SpScreen {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (!isGamepad) {
                SpTopBar(title = "Downloads", showBack = true, onBack = onBack)
            }

            CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
            SpScreenContentList(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SpSpacing.ScreenHorizontal,
                end = SpSpacing.ScreenHorizontal,
                top = sectionPillClearance() + SpSpacing.Default,
                bottom = SpSpacing.Default,
            ),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            // Gamepad-mode screen heading (#1529) — scrolls away with the list
            // so content still passes under the floating pill. Guarded so touch
            // mode (which has the SpTopBar title) gets no empty list row.
            if (isGamepad) {
                item(key = "screen_heading") {
                    SpScreenHeading(title = "Downloads")
                }
            }
            // Cache info card
            item {
                SpCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpSpacing.Default)
                            .semantics {
                                    contentDescription = "Local cache, ${if (state.cacheSize <= 0) "0 B" else "${formatBytes(state.cacheSize)} used"}"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Local Cache",
                                style = SpTypography.TitleLarge,
                                color = SpColor.OnCard,
                            )
                            Text(
                                text = if (state.cacheSize <= 0) "0 B" else "${formatBytes(state.cacheSize)} used",
                                style = SpTypography.BodySmall,
                                color = SpColor.OnBackgroundTertiary,
                            )
                        }
                        SpSecondaryButton(
                            text = "Clear",
                            onClick = { viewModel.onIntent(DownloadsIntent.ClearCache) },
                            isLoading = state.isClearingCache,
                            enabled = !state.isClearingCache && state.cacheSize > 0,
                            modifier = Modifier.focusRestoreItem(
                                key = "downloads_clear_cache",
                                isDefault = true,
                            ),
                        )
                    }
                }
            }

            // Active downloads
            if (state.activeDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "Active Downloads",
                        style = SpTypography.HeadlineSmall,
                        color = SpColor.OnBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                }

                items(state.activeDownloads, key = { it.gameId }) { download ->
                    DownloadItem(
                        download = download,
                        isCancelling = download.gameId in state.cancellingGameIds,
                        onCancel = { viewModel.onIntent(DownloadsIntent.CancelDownload(download.gameId)) },
                        onResume = { viewModel.onIntent(DownloadsIntent.ResumeDownload(download.gameId)) },
                        onRestart = { viewModel.onIntent(DownloadsIntent.RestartDownload(download.gameId, download.gameTitle)) },
                        onRemove = { viewModel.onIntent(DownloadsIntent.RemoveDownload(download.gameId)) },
                        modifier = Modifier.focusRestoreItem(key ="download_${download.gameId}"),
                    )
                }
            }

            // Downloaded games
            if (state.downloadedGames.isNotEmpty()) {
                item {
                    Text(
                        text = "Downloaded Games",
                        style = SpTypography.HeadlineSmall,
                        color = SpColor.OnBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                }

                items(state.downloadedGames, key = { it.gameId }) { game ->
                    DownloadedGameItem(
                        game = game,
                        onClick = { onGameClick(game.gameId) },
                        onDelete = { viewModel.onIntent(DownloadsIntent.DeleteLocalGame(game.gameId)) },
                        modifier = Modifier.focusRestoreItem(key ="downloaded_${game.gameId}"),
                    )
                }
            }

            // Empty state
            if (state.activeDownloads.isEmpty() && state.downloadedGames.isEmpty() && !state.isLoading) {
                item {
                    SpEmptyStates.NoActiveDownloads(modifier = Modifier.fillMaxWidth())
                }
            }
        }
        } // CompositionLocalProvider
        }
    }
}

/** Status label + optional helper line for a download row, by state + cause (#1296). */
private data class DownloadStatusCopy(val label: String, val helper: String?)

private fun downloadStatusCopy(d: DownloadProgress): DownloadStatusCopy {
    val pct = if (d.totalBytes > 0) (d.progress * 100).toInt().coerceIn(0, 100) else null
    val resumeFrom = pct?.let { "Resume from $it%" } ?: "Resume where it left off"
    return when (d.state) {
        DownloadState.DOWNLOADING -> DownloadStatusCopy("Downloading", null)
        DownloadState.QUEUED -> DownloadStatusCopy("Queued", null)
        DownloadState.COMPLETED -> DownloadStatusCopy("Completed", null)
        DownloadState.IDLE -> DownloadStatusCopy("Idle", null)
        DownloadState.PAUSED -> when (d.failureReason) {
            DownloadFailureReason.NETWORK -> DownloadStatusCopy("Connection lost", resumeFrom)
            DownloadFailureReason.SERVER -> DownloadStatusCopy("Server interrupted", resumeFrom)
            else -> DownloadStatusCopy("Paused", resumeFrom)
        }
        DownloadState.FAILED -> when (d.failureReason) {
            DownloadFailureReason.DISK_FULL -> DownloadStatusCopy("Not enough space", "Free up space, then start over")
            DownloadFailureReason.CORRUPT -> DownloadStatusCopy("Download corrupted", "Start over to fix it")
            else -> DownloadStatusCopy("Download failed", "Start over")
        }
    }
}

@Composable
private fun DownloadItem(
    download: DownloadProgress,
    isCancelling: Boolean = false,
    onCancel: () -> Unit,
    onResume: () -> Unit = {},
    onRestart: () -> Unit = {},
    onRemove: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val copy = downloadStatusCopy(download)
    val displayTitle = download.gameTitle.ifEmpty { "Game ${download.gameId}" }

    SpCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default)
                .semantics {
                    contentDescription = "$displayTitle, ${copy.label}" +
                            when {
                                download.state == DownloadState.DOWNLOADING && download.isIndeterminate -> ", downloading"
                                download.state == DownloadState.DOWNLOADING || download.state == DownloadState.PAUSED ->
                                    if (download.totalBytes > 0) ", ${(download.progress * 100).toInt()} percent" else ""
                                else -> ""
                            }
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when (download.state) {
                                DownloadState.DOWNLOADING -> SpColor.DownloadActive
                                DownloadState.QUEUED -> SpColor.DownloadQueued
                                DownloadState.PAUSED -> SpColor.DownloadPaused
                                DownloadState.COMPLETED -> SpColor.DownloadComplete
                                DownloadState.FAILED -> SpColor.DownloadFailed
                                DownloadState.IDLE -> SpColor.DownloadIdle
                            }
                        ),
                )

                Spacer(Modifier.width(SpSpacing.Medium))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = SpTypography.TitleMedium,
                        color = SpColor.OnCard,
                    )
                    Text(
                        text = copy.helper ?: copy.label,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }

                // Primary action by state. Cancel pauses (keeps the partial);
                // Resume continues a paused partial; Start over re-downloads a
                // terminally-failed game from scratch. (#1296)
                when (download.state) {
                    DownloadState.DOWNLOADING, DownloadState.QUEUED -> {
                        val queued = download.state == DownloadState.QUEUED
                        SpButton(
                            text = when {
                                isCancelling -> if (queued) "Cancelling…" else "Pausing…"
                                queued -> "Cancel"
                                else -> "Pause"
                            },
                            onClick = onCancel,
                            style = SpButtonStyle.Ghost,
                            isLoading = isCancelling,
                            enabled = !isCancelling,
                        )
                    }
                    DownloadState.PAUSED -> SpButton(
                        text = "Resume",
                        onClick = onResume,
                        style = SpButtonStyle.Primary,
                    )
                    DownloadState.FAILED -> SpButton(
                        text = "Start over",
                        onClick = onRestart,
                        style = SpButtonStyle.Primary,
                    )
                    else -> {}
                }
            }

            // Progress bar for an in-flight download AND for a paused one, so
            // the user sees how far the resume will continue from. (#1296)
            if (download.state == DownloadState.DOWNLOADING || download.state == DownloadState.PAUSED) {
                Spacer(Modifier.height(SpSpacing.Medium))
                SpDownloadProgressBar(
                    progress = download.progress,
                    bytesDownloaded = download.bytesDownloaded,
                    totalBytes = download.totalBytes,
                    bytesPerSecond = if (download.state == DownloadState.PAUSED) 0 else download.bytesPerSecond,
                    paused = download.state == DownloadState.PAUSED,
                )
            }

            // Secondary "Remove" for paused/failed partials — reclaim disk space
            // without resuming. (#1296)
            if (download.state == DownloadState.PAUSED || download.state == DownloadState.FAILED) {
                Spacer(Modifier.height(SpSpacing.Small))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SpButton(
                        text = "Remove download",
                        onClick = onRemove,
                        style = SpButtonStyle.Ghost,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedGameItem(
    game: DownloadedGame,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${game.title}, ${game.consoleName}, ${formatBytes(game.fileSizeBytes)}"
                role = Role.Button
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = game.title,
                modifier = Modifier.size(48.dp),
                cornerRadius = SpSpacing.RadiusSmall,
            )

            Spacer(Modifier.width(SpSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnCard,
                )
                Text(
                    text = buildString {
                        if (game.consoleName.isNotEmpty()) append(game.consoleName)
                        if (game.fileSizeBytes > 0) {
                            if (isNotEmpty()) append(" - ")
                            append(formatBytes(game.fileSizeBytes))
                        }
                    },
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }

            SpButton(
                text = "Delete",
                onClick = onDelete,
                style = SpButtonStyle.Ghost,
            )
        }
    }
}
