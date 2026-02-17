package com.spela.player.presentation.ui.screen

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpDownloadProgressBar
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatBytes
import com.spela.player.presentation.viewmodel.DownloadsIntent
import com.spela.player.presentation.viewmodel.DownloadsViewModel

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(DownloadsIntent.LoadDownloads)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(title = "Downloads", showBack = true, onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                        SpButton(
                            text = "Clear",
                            onClick = { viewModel.onIntent(DownloadsIntent.ClearCache) },
                            style = SpButtonStyle.Outlined,
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
                        onCancel = { viewModel.onIntent(DownloadsIntent.CancelDownload(download.gameId)) },
                    )
                }
            }

            // Empty state
            if (state.activeDownloads.isEmpty() && !state.isLoading) {
                item {
                    SpEmptyStates.NoActiveDownloads(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(
    download: DownloadProgress,
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
                                ", ${(download.progress * 100).toInt()} percent" else ""
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
                        text = "Cancel",
                        onClick = onCancel,
                        style = SpButtonStyle.Ghost,
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
