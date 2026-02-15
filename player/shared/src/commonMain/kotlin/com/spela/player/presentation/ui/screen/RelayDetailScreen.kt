package com.spela.player.presentation.ui.screen

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.RelayDetail
import com.spela.player.domain.model.RelayMember
import com.spela.player.domain.model.RelaySave
import com.spela.player.presentation.intent.RelayDetailIntent
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.RelayDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayDetailScreen(
    relayId: String,
    viewModel: RelayDetailViewModel,
    onBack: () -> Unit,
    onPlay: (gameId: String, relayId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(relayId) {
        viewModel.onIntent(RelayDetailIntent.LoadRelay(relayId))
        viewModel.onIntent(RelayDetailIntent.LoadSaves(relayId))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = state.relay?.name ?: "Relay",
                showBack = true,
                onBack = onBack,
            )

            if (state.isLoadingRelay && state.relay == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading relay...")
                }
            } else if (state.relay != null) {
                val relay = state.relay!!
                val isRefreshing = state.isLoadingRelay || state.isLoadingSaves

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        viewModel.onIntent(RelayDetailIntent.LoadRelay(relayId))
                        viewModel.onIntent(RelayDetailIntent.LoadSaves(relayId))
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = SpSpacing.Default),
                    ) {
                        // Game info header
                        item {
                            RelayHeader(
                                relay = relay,
                                isTakingTurn = state.isTakingTurn,
                                isReleasingTurn = state.isReleasingTurn,
                                hasActiveTurn = state.turnToken != null,
                                onTakeTurn = { viewModel.onIntent(RelayDetailIntent.TakeTurn(relayId)) },
                                onReleaseTurn = { viewModel.onIntent(RelayDetailIntent.ReleaseTurn(relayId)) },
                                onPlay = { onPlay(relay.gameId, relayId) },
                            )
                            Spacer(Modifier.height(SpSpacing.XLarge))
                        }

                        // Members section
                        item {
                            SectionHeader(
                                title = "Members",
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            )
                            Spacer(Modifier.height(SpSpacing.Small))
                        }
                        items(
                            relay.members,
                            key = { "member-${it.userId}" },
                        ) { member ->
                            MemberItem(
                                member = member,
                                isActive = member.userId == relay.activeUserId,
                            )
                        }

                        // Invite section
                        item {
                            Spacer(Modifier.height(SpSpacing.Default))
                            InviteSection(
                                isInviting = state.isInviting,
                                onInvite = { username ->
                                    viewModel.onIntent(RelayDetailIntent.InviteUser(relayId, username))
                                },
                            )
                            Spacer(Modifier.height(SpSpacing.XLarge))
                        }

                        // Saves section
                        item {
                            SectionHeader(
                                title = "Save States",
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                            )
                            Spacer(Modifier.height(SpSpacing.Small))
                        }

                        if (state.saves.isEmpty() && !state.isLoadingSaves) {
                            item {
                                SpEmptyState(
                                    icon = Icons.Filled.Save,
                                    title = "No saves yet",
                                    message = "Take a turn and play to create the first save state",
                                )
                            }
                        } else {
                            items(
                                state.saves,
                                key = { "save-${it.id}" },
                            ) { save ->
                                SaveItem(save = save)
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
                    onAction = { viewModel.onIntent(RelayDetailIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(RelayDetailIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SpSnackbar(
            data = state.successMessage?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Success,
                )
            },
            onDismiss = { viewModel.onIntent(RelayDetailIntent.DismissSuccess) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = SpTypography.HeadlineLarge,
        color = SpColor.OnBackground,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun RelayHeader(
    relay: RelayDetail,
    isTakingTurn: Boolean,
    isReleasingTurn: Boolean,
    hasActiveTurn: Boolean,
    onTakeTurn: () -> Unit,
    onReleaseTurn: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpCoverArt(
                imageUrl = relay.gameCoverUrl,
                contentDescription = "${relay.gameTitle} cover",
                modifier = Modifier.size(width = 80.dp, height = 107.dp),
                cornerRadius = 12.dp,
            )
            Spacer(Modifier.width(SpSpacing.Default))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relay.gameTitle,
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (relay.gameConsoleName.isNotEmpty()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = relay.gameConsoleName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
                Spacer(Modifier.height(SpSpacing.Small))
                Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                    SpChip(
                        text = relay.status.replaceFirstChar { it.uppercase() },
                        color = when (relay.status) {
                            "active" -> SpColor.Success
                            "paused" -> SpColor.Warning
                            else -> SpColor.OnBackgroundTertiary
                        },
                    )
                    SpChip(text = "${relay.memberCount} members")
                }
            }
        }

        if (relay.description.isNotEmpty()) {
            Spacer(Modifier.height(SpSpacing.Default))
            Text(
                text = relay.description,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )
        }

        Spacer(Modifier.height(SpSpacing.Default))

        // Turn controls
        val isMyTurn = hasActiveTurn
        val turnHeld = relay.activeUserId != null
        val activeUser = relay.members.find { it.userId == relay.activeUserId }

        if (turnHeld && !isMyTurn) {
            SpCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpSpacing.Default),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${activeUser?.username ?: "Someone"} is currently playing",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnCard,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (isMyTurn) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium)) {
                SpButton(
                    text = "Play",
                    onClick = onPlay,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                SpButton(
                    text = "Release Turn",
                    onClick = onReleaseTurn,
                    style = SpButtonStyle.Outlined,
                    isLoading = isReleasingTurn,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        } else {
            SpButton(
                text = "Take Turn",
                onClick = onTakeTurn,
                isLoading = isTakingTurn,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun MemberItem(
    member: RelayMember,
    isActive: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "${member.username}, ${member.role}" +
                        if (isActive) ", currently playing" else ""
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (member.isOnline) SpColor.Success.copy(alpha = 0.2f) else SpColor.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = if (member.isOnline) SpColor.Success else SpColor.OnBackgroundTertiary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(SpSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.username,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnBackground,
                )
                if (member.role == "owner") {
                    Spacer(Modifier.width(SpSpacing.Small))
                    SpChip(text = "Owner", color = SpColor.Primary)
                }
            }
            if (isActive) {
                Text(
                    text = "Currently playing",
                    style = SpTypography.BodySmall,
                    color = SpColor.Success,
                )
            } else if (member.isOnline) {
                Text(
                    text = "Online",
                    style = SpTypography.BodySmall,
                    color = SpColor.Success,
                )
            }
        }
    }
}

@Composable
private fun InviteSection(
    isInviting: Boolean,
    onInvite: (String) -> Unit,
) {
    var username by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
    ) {
        Text(
            text = "Invite a friend",
            style = SpTypography.TitleLarge,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.Small))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            SpTextField(
                value = username,
                onValueChange = { username = it },
                placeholder = "Username",
                modifier = Modifier.weight(1f),
            )
            SpButton(
                text = "Invite",
                onClick = {
                    if (username.isNotBlank()) {
                        onInvite(username.trim())
                        username = ""
                    }
                },
                isLoading = isInviting,
                enabled = username.isNotBlank(),
            )
        }
    }
}

@Composable
private fun SaveItem(
    save: RelaySave,
) {
    SpCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                tint = SpColor.Primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = save.name,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = "by ${save.username}",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
            if (save.isAuto) {
                SpChip(text = "Auto", color = SpColor.Primary)
            }
        }
    }
}
