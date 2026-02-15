package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.NetplaySession
import com.spela.player.domain.model.NetplaySessionStatus
import com.spela.player.presentation.intent.NetplayLobbyIntent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpConsoleChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpNetplayPlayerSlot
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.components.SpSessionCode
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.NetplayLobbyViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetplayLobbyScreen(
    sessionId: String,
    viewModel: NetplayLobbyViewModel,
    onBack: () -> Unit,
    currentUserId: String,
    onStartGame: (gameId: String, sessionId: String, localPort: Int, inputDelay: Int, isHost: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(sessionId) {
        viewModel.onIntent(NetplayLobbyIntent.LoadSession(sessionId))
    }

    // Navigate back when session is left
    LaunchedEffect(state.sessionLeft) {
        if (state.sessionLeft) {
            onBack()
        }
    }

    // Auto-launch countdown: when both players are connected, count down from 3 and launch
    val session = state.session
    val bothConnected = session != null &&
        session.status == NetplaySessionStatus.WAITING &&
        session.clientUserId != null
    var autoLaunchCountdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(bothConnected, session?.inputDelay) {
        if (bothConnected && session != null) {
            autoLaunchCountdown = 3
            while (autoLaunchCountdown > 0) {
                delay(1000)
                autoLaunchCountdown--
            }
            val isHost = session.hostUserId == currentUserId
            val localPort = if (isHost) 0 else 1
            onStartGame(session.gameId, session.id, localPort, session.inputDelay, isHost)
        } else {
            autoLaunchCountdown = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = "Netplay Lobby",
                showBack = true,
                onBack = onBack,
            )

            if (state.isLoading && session == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading session...")
                }
            } else if (session != null) {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.onIntent(NetplayLobbyIntent.LoadSession(sessionId)) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = SpSpacing.Default),
                    ) {
                        // Game header
                        item {
                            LobbyHeader(session = session)
                            Spacer(Modifier.height(SpSpacing.XLarge))
                        }

                        // Invite code (visible while waiting)
                        if (session.status == NetplaySessionStatus.WAITING && session.inviteCode.isNotEmpty()) {
                            item {
                                SpSessionCode(
                                    code = session.inviteCode,
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(session.inviteCode))
                                    },
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                                Spacer(Modifier.height(SpSpacing.XLarge))
                            }
                        }

                        // Players section
                        item {
                            Text(
                                text = "Players",
                                style = SpTypography.HeadlineLarge,
                                color = SpColor.OnBackground,
                                modifier = Modifier
                                    .padding(horizontal = SpSpacing.ScreenHorizontal)
                                    .semantics { heading() },
                            )
                            Spacer(Modifier.height(SpSpacing.Small))
                        }

                        // Host slot
                        item {
                            SpNetplayPlayerSlot(
                                username = session.hostUsername,
                                avatarUrl = session.hostAvatarUrl,
                                isHost = true,
                                latencyMs = null,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            )
                            Spacer(Modifier.height(SpSpacing.Small))
                        }

                        // Client slot with animated visibility
                        item {
                            Column {
                                AnimatedVisibility(
                                    visible = session.clientUserId != null,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically(),
                                ) {
                                    SpNetplayPlayerSlot(
                                        username = session.clientUsername,
                                        avatarUrl = session.clientAvatarUrl,
                                        isHost = false,
                                        latencyMs = null,
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                }
                                AnimatedVisibility(
                                    visible = session.clientUserId == null,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically(),
                                ) {
                                    SpNetplayPlayerSlot(
                                        username = null,
                                        avatarUrl = null,
                                        isHost = false,
                                        latencyMs = null,
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                }
                            }
                        }

                        // Auto-launch countdown
                        if (bothConnected) {
                            item {
                                Spacer(Modifier.height(SpSpacing.XLarge))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                                        .semantics {
                                            liveRegion = LiveRegionMode.Polite
                                            contentDescription = "Starting game in $autoLaunchCountdown seconds"
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = if (autoLaunchCountdown > 0) {
                                            "Starting game in ${autoLaunchCountdown}..."
                                        } else {
                                            "Starting game..."
                                        },
                                        style = SpTypography.TitleLarge,
                                        color = SpColor.Primary,
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                    SpProgressBar(
                                        progress = 1f - (autoLaunchCountdown / 3f),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        // Input delay section
                        item {
                            Spacer(Modifier.height(SpSpacing.XLarge))
                            InputDelaySection(
                                inputDelay = session.inputDelay,
                                isHost = session.hostUserId == currentUserId,
                                isUpdating = state.isUpdatingSettings,
                                onChangeDelay = { newDelay ->
                                    viewModel.onIntent(NetplayLobbyIntent.UpdateInputDelay(newDelay))
                                },
                            )
                        }

                        // Action buttons
                        item {
                            Spacer(Modifier.height(SpSpacing.XLarge))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SpSpacing.ScreenHorizontal),
                                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                            ) {
                                SpButton(
                                    text = if (session.clientUserId == null) "Cancel" else "Leave",
                                    onClick = { viewModel.onIntent(NetplayLobbyIntent.LeaveSession) },
                                    style = SpButtonStyle.Outlined,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error snackbar
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(NetplayLobbyIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(NetplayLobbyIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun LobbyHeader(session: NetplaySession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpCoverArt(
            imageUrl = session.gameCoverUrl,
            contentDescription = "${session.gameTitle} cover",
            modifier = Modifier.size(width = 80.dp, height = 107.dp),
            cornerRadius = 12.dp,
        )
        Spacer(Modifier.width(SpSpacing.Default))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.gameTitle,
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (session.gameConsoleName.isNotEmpty()) {
                Spacer(Modifier.height(SpSpacing.XXSmall))
                SpConsoleChip(
                    consoleName = session.gameConsoleName,
                    consoleColor = getConsoleColor(session.gameConsoleName),
                )
            }
            Spacer(Modifier.height(SpSpacing.Small))
            SpChip(
                text = when (session.status) {
                    NetplaySessionStatus.WAITING -> "Waiting"
                    NetplaySessionStatus.IN_PROGRESS -> "In Progress"
                    NetplaySessionStatus.ENDED -> "Ended"
                },
                color = when (session.status) {
                    NetplaySessionStatus.WAITING -> SpColor.Warning
                    NetplaySessionStatus.IN_PROGRESS -> SpColor.Success
                    NetplaySessionStatus.ENDED -> SpColor.OnBackgroundTertiary
                },
            )
        }
    }
}

@Composable
private fun InputDelaySection(
    inputDelay: Int,
    isHost: Boolean,
    isUpdating: Boolean,
    onChangeDelay: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
    ) {
        Text(
            text = "Input Delay",
            style = SpTypography.HeadlineLarge,
            color = SpColor.OnBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(SpSpacing.Small))

        SpCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpSpacing.Default),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isHost) {
                    SpButton(
                        text = "",
                        onClick = { if (inputDelay > 1) onChangeDelay(inputDelay - 1) },
                        style = SpButtonStyle.Outlined,
                        enabled = inputDelay > 1 && !isUpdating,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Decrease input delay",
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )

                    Text(
                        text = "$inputDelay frames",
                        style = SpTypography.TitleLarge,
                        color = SpColor.Primary,
                        modifier = Modifier.semantics {
                            contentDescription = "Input delay: $inputDelay frames"
                        },
                    )

                    SpButton(
                        text = "",
                        onClick = { if (inputDelay < 10) onChangeDelay(inputDelay + 1) },
                        style = SpButtonStyle.Outlined,
                        enabled = inputDelay < 10 && !isUpdating,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Increase input delay",
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                } else {
                    Text(
                        text = "Input Delay",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnCard,
                    )
                    Text(
                        text = "$inputDelay frames (set by host)",
                        style = SpTypography.TitleLarge,
                        color = SpColor.Primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(SpSpacing.XSmall))
        Text(
            text = "Higher delay = smoother online play. Lower delay = more responsive controls. Start with 3 and adjust if you notice stuttering.",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}
