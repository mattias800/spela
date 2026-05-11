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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.presentation.intent.SharedSessionIntent
import com.spela.player.presentation.ui.feature.sharedsession.SharedSessionStatusChip
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.ScreenLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SharedSessionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSessionsScreen(
    viewModel: SharedSessionsViewModel,
    onSharedSessionSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(SharedSessionIntent.RefreshAll)
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
                    title = "Shared Sessions",
                    showBack = true,
                    onBack = onBack,
                )
            }

            val isLoading = state.isLoadingSharedSessions || state.isLoadingInvitations
            val isEmpty = state.sharedSessions.isEmpty() && state.invitations.isEmpty()

            if (isLoading && isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ScreenLoadingIndicator(message = "Loading shared sessions...")
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.onIntent(SharedSessionIntent.RefreshAll) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (isEmpty && !isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpEmptyState(
                                icon = Icons.Filled.SyncAlt,
                                title = "No shared sessions yet",
                                message = "Create a shared session from a game's detail page and invite friends to take turns playing",
                            )
                        }
                    } else {
                        val focusMemory = rememberFocusMemoryState()
                        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = SpSpacing.Default),
                        ) {
                            // Invitations section
                            if (state.invitations.isNotEmpty()) {
                                item {
                                    SpSectionHeader(
                                        title = "Invitations",
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                }
                                items(
                                    state.invitations,
                                    key = { "invite-${it.id}" },
                                ) { invitation ->
                                    InvitationItem(
                                        invitation = invitation,
                                        onAccept = { viewModel.onIntent(SharedSessionIntent.AcceptInvitation(invitation.id)) },
                                        onReject = { viewModel.onIntent(SharedSessionIntent.RejectInvitation(invitation.id)) },
                                        modifier = Modifier
                                            .padding(horizontal = SpSpacing.ScreenHorizontal)
                                            .focusRestoreItem(
                                                key = "invite_${invitation.id}",
                                                isDefault = invitation == state.invitations.firstOrNull(),
                                            ),
                                    )
                                }
                                item {
                                    Spacer(Modifier.height(SpSpacing.XLarge))
                                }
                            }

                            // My Shared Sessions section
                            if (state.sharedSessions.isNotEmpty()) {
                                item {
                                    SpSectionHeader(
                                        title = "My Shared Sessions",
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                }
                                items(
                                    state.sharedSessions,
                                    key = { "shared-session-${it.id}" },
                                ) { sharedSession ->
                                    SharedSessionItem(
                                        sharedSession = sharedSession,
                                        onClick = { onSharedSessionSelected(sharedSession.id) },
                                        modifier = Modifier
                                            .padding(horizontal = SpSpacing.ScreenHorizontal)
                                            .focusRestoreItem(
                                                key = "shared_${sharedSession.id}",
                                                isDefault = state.invitations.isEmpty() &&
                                                    sharedSession == state.sharedSessions.firstOrNull(),
                                            ),
                                    )
                                }
                            }
                        }
                        } // CompositionLocalProvider
                    }
                }
            }
        }

        // Snackbars
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(SharedSessionIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(SharedSessionIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SpSnackbar(
            data = state.successMessage?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Success,
                )
            },
            onDismiss = { viewModel.onIntent(SharedSessionIntent.DismissSuccess) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SharedSessionItem(
    sharedSession: SharedSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.XSmall)
            .semantics {
                contentDescription = "Shared Session: ${sharedSession.name}, game: ${sharedSession.gameTitle}"
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
                imageUrl = sharedSession.gameCoverUrl,
                contentDescription = "${sharedSession.gameTitle} cover",
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                cornerRadius = SpSpacing.RadiusMedium,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sharedSession.name,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = sharedSession.gameTitle,
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SharedSessionStatusChip(status = sharedSession.status)
                    SpChip(
                        text = "${sharedSession.memberCount} members",
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitationItem(
    invitation: SharedSessionInvitation,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.XSmall)
            .semantics {
                contentDescription = "${invitation.inviterUsername} invited you to ${invitation.sharedSessionName}"
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = invitation.gameCoverUrl,
                contentDescription = "${invitation.gameTitle} cover",
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                cornerRadius = SpSpacing.RadiusMedium,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = invitation.sharedSessionName,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = "Invited by ${invitation.inviterUsername}",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = invitation.gameTitle,
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
                Spacer(Modifier.height(SpSpacing.Small))
                Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                    SpButton(
                        text = "Accept",
                        onClick = onAccept,
                    )
                    SpButton(
                        text = "Decline",
                        onClick = onReject,
                        style = SpButtonStyle.Ghost,
                    )
                }
            }
        }
    }
}
