package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.NetplaySessionStatus
import com.spela.player.presentation.intent.NetplayLobbyIntent
import com.spela.player.presentation.ui.feature.netplay.InputDelaySection
import com.spela.player.presentation.ui.feature.netplay.LobbyHeader
import com.spela.player.presentation.ui.components.InvitePlayerSheet
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.createTextClipEntry
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.SpNetplayPlayerSlot
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.components.SpSessionCode
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.NetplayLobbyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class NetplayStartConfig(
    val gameId: String,
    val sessionId: String,
    val localPort: Int,
    val inputDelay: Int,
    val isHost: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetplayLobbyScreen(
    sessionId: String,
    viewModel: NetplayLobbyViewModel,
    onBack: () -> Unit,
    currentUserId: String,
    onStartGame: (NetplayStartConfig) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

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
        if (bothConnected) {
            autoLaunchCountdown = 3
            while (autoLaunchCountdown > 0) {
                delay(1000)
                autoLaunchCountdown--
            }
            val isHost = session.hostUserId == currentUserId
            val localPort = if (isHost) 0 else 1
            onStartGame(NetplayStartConfig(
                gameId = session.gameId,
                sessionId = session.id,
                localPort = localPort,
                inputDelay = session.inputDelay,
                isHost = isHost,
            ))
        } else {
            autoLaunchCountdown = 0
        }
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    SpScreen {
        val focusMemory = rememberFocusMemoryState()
        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(
                    title = "Netplay Lobby",
                    showBack = true,
                    onBack = onBack,
                )
            }

            if (state.isLoading && session == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ScreenLoadingIndicator(message = "Loading session...")
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
                            LobbyHeader(
                                session = session,
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            )
                            Spacer(Modifier.height(SpSpacing.XLarge))
                        }

                        // Invite code (visible while waiting)
                        if (session.status == NetplaySessionStatus.WAITING && session.inviteCode.isNotEmpty()) {
                            item {
                                SpSessionCode(
                                    code = session.inviteCode,
                                    onCopy = {
                                        clipboardScope.launch {
                                            clipboard.setClipEntry(createTextClipEntry(session.inviteCode))
                                        }
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
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            )
                        }

                        // Invite Player button (host only, while waiting, no client yet)
                        if (session.hostUserId == currentUserId &&
                            session.status == NetplaySessionStatus.WAITING &&
                            session.clientUserId == null
                        ) {
                            item {
                                Spacer(Modifier.height(SpSpacing.Default))
                                SpButton(
                                    text = "Invite Player",
                                    onClick = { viewModel.onIntent(NetplayLobbyIntent.ShowInviteSheet) },
                                    modifier = Modifier
                                        .focusRestoreItem(
                                            key = "netplay_lobby_invite_player",
                                            isDefault = true,
                                        )
                                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                                )
                            }
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
                                SpSecondaryButton(
                                    text = if (session.clientUserId == null) "Cancel" else "Leave",
                                    onClick = { viewModel.onIntent(NetplayLobbyIntent.LeaveSession) },
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

        // Invite success snackbar
        SpSnackbar(
            data = state.inviteSuccessMessage?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Success,
                )
            },
            onDismiss = { viewModel.onIntent(NetplayLobbyIntent.DismissInviteSuccess) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        } // CompositionLocalProvider
    }

    // Invite player dialog
    if (state.showInviteSheet) {
        InvitePlayerSheet(
            searchQuery = state.inviteSearchQuery,
            onSearchQueryChange = { viewModel.onIntent(NetplayLobbyIntent.UpdateInviteSearchQuery(it)) },
            searchResults = state.inviteSearchResults,
            searchTotal = state.inviteSearchTotal,
            searchPage = state.inviteSearchPage,
            recentPartners = state.recentPartners,
            isSearching = state.isSearchingUsers,
            isLoadingRecentPartners = state.isLoadingRecentPartners,
            invitingUsername = state.invitingUsername,
            invitedUsernames = state.invitedUsernames,
            onInvite = { viewModel.onIntent(NetplayLobbyIntent.SendInvite(it)) },
            onPageChange = { viewModel.onIntent(NetplayLobbyIntent.InviteSearchPage(it)) },
            onDismiss = { viewModel.onIntent(NetplayLobbyIntent.HideInviteSheet) },
        )
    }
}
