package com.spela.player.presentation.ui.screen

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.NetplaySession
import com.spela.player.presentation.intent.NetplayIntent
import com.spela.player.presentation.ui.feature.netplay.NetplayStatusChip
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.NetplayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetplayListScreen(
    viewModel: NetplayViewModel,
    onSessionSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.onIntent(NetplayIntent.LoadSessions)
    }

    // Navigate to lobby when session is joined/created
    LaunchedEffect(state.joinedSession) {
        state.joinedSession?.let { session ->
            onSessionSelected(session.id)
        }
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
                SpTopBar(
                    title = "Netplay",
                    showBack = true,
                    onBack = onBack,
                )
            }

            if (state.isLoading && state.sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading sessions...")
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.onIntent(NetplayIntent.LoadSessions) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val focusMemory = rememberFocusMemoryState()
                    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = SpSpacing.Default),
                    ) {
                        // Action buttons
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = SpSpacing.ScreenHorizontal),
                                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                            ) {
                                SpSecondaryButton(
                                    text = "Join by Code",
                                    onClick = { showJoinDialog = true },
                                    modifier = Modifier.autoFocus(),
                                )
                            }
                            Spacer(Modifier.height(SpSpacing.Default))
                        }

                        if (state.sessions.isEmpty() && !state.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = SpSpacing.XXLarge),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SpEmptyStates.NoNetplaySessions()
                                }
                            }
                        } else {
                            items(
                                state.sessions,
                                key = { "session-${it.id}" },
                            ) { session ->
                                NetplaySessionItem(
                                    session = session,
                                    onClick = { onSessionSelected(session.id) },
                                    modifier = Modifier.rememberFocus("session_${session.id}"),
                                )
                            }
                        }
                    }
                    } // CompositionLocalProvider
                }
            }
        }

        // Join by code dialog
        if (showJoinDialog) {
            SpDialog(
                title = "Join by Invite Code",
                onDismiss = {
                    inviteCode = ""
                    showJoinDialog = false
                },
                confirmText = "Join",
                dismissText = "Cancel",
                onConfirm = {
                    if (inviteCode.isNotBlank()) {
                        viewModel.onIntent(NetplayIntent.JoinByCode(inviteCode.trim()))
                        inviteCode = ""
                        showJoinDialog = false
                    }
                },
            ) {
                SpTextField(
                    value = inviteCode,
                    onValueChange = { newValue ->
                        if (newValue.length <= 6) {
                            inviteCode = newValue.uppercase()
                        }
                    },
                    placeholder = "Enter invite code",
                )
            }
        }

        // Error snackbar
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(NetplayIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(NetplayIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun NetplaySessionItem(
    session: NetplaySession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
            .semantics {
                contentDescription = "Session: ${session.gameTitle}, host: ${session.hostUsername}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = session.gameCoverUrl,
                contentDescription = "${session.gameTitle} cover",
                modifier = Modifier.width(48.dp),
                cornerRadius = SpSpacing.RadiusMedium,
                aspectRatio = session.coverAspectRatio,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.gameTitle,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    Text(
                        text = "Host: ${session.hostUsername}",
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(SpSpacing.XSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NetplayStatusChip(status = session.status)
                    if (session.clientUserId != null) {
                        SpChip(text = "2 players")
                    } else {
                        SpChip(text = "1 player")
                    }
                }
            }
        }
    }
}
