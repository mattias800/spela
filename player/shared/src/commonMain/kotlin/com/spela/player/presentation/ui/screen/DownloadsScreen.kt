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
import androidx.compose.foundation.lazy.LazyColumn
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
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.autoFocus
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

    SpScreen {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(title = "Downloads", showBack = true, onBack = onBack)
            }

            LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Default,
            ),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
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
                            modifier = Modifier.autoFocus(),
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
        }
    }
}

@Composable
private fun DownloadItem(
    download: DownloadProgress,
    isCancelling: Boolean = false,
    onCancel: () -> Unit,
) {
    val statusText = download.state.name.lowercase().replaceFirstChar { it.uppercase() }
    val displayTitle = download.gameTitle.ifEmpty { "Game ${download.gameId}" }

    SpCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default)
                .semantics {
                    contentDescription = "$displayTitle, $statusText" +
                            if (download.state == DownloadState.DOWNLOADING)
                                if (download.isIndeterminate) ", downloading" else ", ${(download.progress * 100).toInt()} percent"
                            else ""
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
                        text = statusText,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }

                if (download.state == DownloadState.DOWNLOADING || download.state == DownloadState.QUEUED) {
                    SpButton(
                        text = if (isCancelling) "Cancelling..." else "Cancel",
                        onClick = onCancel,
                        style = SpButtonStyle.Ghost,
                        isLoading = isCancelling,
                        enabled = !isCancelling,
                    )
                }
            }

            if (download.state == DownloadState.DOWNLOADING) {
                Spacer(Modifier.height(SpSpacing.Medium))
                SpDownloadProgressBar(
                    progress = download.progress,
                    bytesDownloaded = download.bytesDownloaded,
                    totalBytes = download.totalBytes,
                )
            }
        }
    }
}

@Composable
private fun DownloadedGameItem(
    game: DownloadedGame,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    SpCard(
        modifier = Modifier
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
