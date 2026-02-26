package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spela.player.presentation.intent.RelayDetailIntent
import com.spela.player.presentation.ui.feature.relay.InviteSection
import com.spela.player.presentation.ui.feature.relay.MemberItem
import com.spela.player.presentation.ui.feature.relay.RelayHeader
import com.spela.player.presentation.ui.feature.relay.RelaySaveItem
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.RelayDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayDetailScreen(
    relayId: String,
    viewModel: RelayDetailViewModel,
    onBack: () -> Unit,
    onPlay: (gameId: String, relayId: String) -> Unit,
) {
    PlatformBackHandler { onBack() }

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
                val relay = state.relay ?: return
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
                            SpSectionHeader(
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
                            SpSectionHeader(
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
                                RelaySaveItem(
                                    save = save,
                                    isCopying = state.copyingSaveId == save.id,
                                    onCopyToGame = {
                                        viewModel.onIntent(
                                            RelayDetailIntent.CopySaveToGame(relayId, save.id)
                                        )
                                    },
                                )
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

