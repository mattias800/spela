package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.spela.player.domain.model.ImportStatus
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpMainContentPadding
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpScrollableContent
import com.spela.player.presentation.ui.components.SpSectionList
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.RemoteGameDetailIntent
import com.spela.player.presentation.viewmodel.RemoteGameDetailViewModel

private fun importStatusLabel(status: ImportStatus): String =
    when (status) {
        ImportStatus.PENDING -> "Queued"
        ImportStatus.DOWNLOADING -> "Downloading"
        ImportStatus.INGESTING -> "Adding to library"
        ImportStatus.SCRAPING -> "Fetching metadata"
        ImportStatus.COMPLETED -> "Imported"
        ImportStatus.FAILED -> "Failed"
        ImportStatus.UNKNOWN -> "Unknown"
    }

/**
 * Detail for a connected-server game: cover/title/console + how many servers
 * offer it, and a capability-gated Import action with live progress. On a
 * completed import, links into the local library. See #1391.
 */
@Composable
fun RemoteGameDetailScreen(
    gameKey: String,
    viewModel: RemoteGameDetailViewModel,
    onBack: () -> Unit,
    onOpenLocalGame: (String) -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(gameKey) {
        viewModel.onIntent(RemoteGameDetailIntent.Load(gameKey))
    }

    val focusMemory = rememberFocusMemoryState()
    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        SpScreen(modifier = Modifier.testTag("remote_game_detail_screen")) {
            Column(modifier = Modifier.fillMaxSize()) {
                SpTopBar(title = state.game?.title.orEmpty(), showBack = true, onBack = onBack)

                val game = state.game
                if (state.isLoading && game == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ScreenLoadingIndicator(message = "Loading...")
                    }
                } else if (game == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        SpEmptyState(
                            icon = Icons.Default.CloudOff,
                            title = "Game not available",
                            message = "No connected server is currently offering this game.",
                        )
                    }
                } else {
                    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD
                    SpScrollableContent {
                        // Touch mode shows SpTopBar above; only add pill clearance
                        // in gamepad mode (where the top bar is hidden).
                        if (isGamepad) SpScreenTopSpacer()
                        SpMainContentPadding {
                            SpSectionList {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    SpCoverArt(
                                        imageUrl = game.coverUrl,
                                        contentDescription = game.title,
                                        modifier = Modifier.width(SpSpacing.CoverLargeWidth),
                                    )
                                    Spacer(Modifier.height(SpSpacing.Default))
                                    Text(
                                        text = game.title,
                                        style = SpTypography.TitleLarge,
                                        color = SpColor.OnSurface,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(SpSpacing.XSmall))
                                    val servers = game.originCount
                                    Text(
                                        text = "${game.console} · on $servers connected ${if (servers == 1) "server" else "servers"}",
                                        style = SpTypography.BodyMedium,
                                        color = SpColor.OnSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }

                                ImportActionArea(
                                    state = state,
                                    onImport = { viewModel.onIntent(RemoteGameDetailIntent.StartImport) },
                                    onOpenLocalGame = onOpenLocalGame,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportActionArea(
    state: com.spela.player.presentation.viewmodel.RemoteGameDetailState,
    onImport: () -> Unit,
    onOpenLocalGame: (String) -> Unit,
) {
    val job = state.currentJob
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            job?.status == ImportStatus.COMPLETED && job.gameId != null -> {
                SpButton(
                    text = "View in library",
                    onClick = { onOpenLocalGame(job.gameId.toString()) },
                    modifier = Modifier.testTag("open_imported_game_button"),
                )
            }

            job != null && job.status.isActive -> {
                Text(
                    text = importStatusLabel(job.status),
                    style = SpTypography.LabelLarge,
                    color = SpColor.OnSurfaceVariant,
                )
                if (job.status == ImportStatus.DOWNLOADING && job.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { job.bytesDownloaded.toFloat() / job.totalBytes.toFloat() },
                        modifier = Modifier.fillMaxWidth().testTag("import_progress"),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("import_progress"))
                }
            }

            state.canImport -> {
                if (job?.status == ImportStatus.FAILED && !job.errorMessage.isNullOrEmpty()) {
                    Text(
                        text = "Last import failed: ${job.errorMessage}",
                        style = SpTypography.BodySmall,
                        color = SpColor.Error,
                    )
                }
                SpButton(
                    text = if (job?.status == ImportStatus.FAILED) "Retry import" else "Import to library",
                    onClick = onImport,
                    isLoading = state.starting,
                    modifier = Modifier.testTag("import_game_button"),
                )
            }

            else -> {
                Text(
                    text = "You don't have permission to import games. Ask an admin for access.",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
