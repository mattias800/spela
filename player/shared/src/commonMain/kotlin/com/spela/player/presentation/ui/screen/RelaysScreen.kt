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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mail
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Relay
import com.spela.player.domain.model.RelayInvitation
import com.spela.player.presentation.intent.RelayIntent
import com.spela.player.presentation.ui.feature.relay.RelayStatusChip
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.RelaysViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelaysScreen(
    viewModel: RelaysViewModel,
    onRelaySelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(RelayIntent.RefreshAll)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = "Relays",
                showBack = true,
                onBack = onBack,
            )

            val isLoading = state.isLoadingRelays || state.isLoadingInvitations
            val isEmpty = state.relays.isEmpty() && state.invitations.isEmpty()

            if (isLoading && isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading relays...")
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = { viewModel.onIntent(RelayIntent.RefreshAll) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (isEmpty && !isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpEmptyState(
                                icon = Icons.Filled.SyncAlt,
                                title = "No relays yet",
                                message = "Create a relay from a game's detail page and invite friends to take turns playing",
                            )
                        }
                    } else {
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
                                        onAccept = { viewModel.onIntent(RelayIntent.AcceptInvitation(invitation.id)) },
                                        onReject = { viewModel.onIntent(RelayIntent.RejectInvitation(invitation.id)) },
                                    )
                                }
                                item {
                                    Spacer(Modifier.height(SpSpacing.XLarge))
                                }
                            }

                            // My Relays section
                            if (state.relays.isNotEmpty()) {
                                item {
                                    SpSectionHeader(
                                        title = "My Relays",
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                }
                                items(
                                    state.relays,
                                    key = { "relay-${it.id}" },
                                ) { relay ->
                                    RelayItem(
                                        relay = relay,
                                        onClick = { onRelaySelected(relay.id) },
                                    )
                                }
                            }
                        }
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
                    onAction = { viewModel.onIntent(RelayIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(RelayIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SpSnackbar(
            data = state.successMessage?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Success,
                )
            },
            onDismiss = { viewModel.onIntent(RelayIntent.DismissSuccess) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun RelayItem(
    relay: Relay,
    onClick: () -> Unit,
) {
    SpCard(
        onGradient = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
            .semantics {
                contentDescription = "Relay: ${relay.name}, game: ${relay.gameTitle}"
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
                imageUrl = relay.gameCoverUrl,
                contentDescription = "${relay.gameTitle} cover",
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                cornerRadius = SpSpacing.RadiusMedium,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relay.name,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = relay.gameTitle,
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
                    RelayStatusChip(status = relay.status)
                    SpChip(
                        text = "${relay.memberCount} members",
                    )
                }
            }
        }
    }
}

@Composable
private fun InvitationItem(
    invitation: RelayInvitation,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    SpCard(
        onGradient = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
            .semantics {
                contentDescription = "${invitation.inviterUsername} invited you to ${invitation.relayName}"
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
                    text = invitation.relayName,
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
