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
import com.spela.player.presentation.intent.SharedSessionDetailIntent
import com.spela.player.presentation.ui.feature.sharedsession.InviteSection
import com.spela.player.presentation.ui.feature.sharedsession.MemberItem
import com.spela.player.presentation.ui.feature.sharedsession.SharedSessionHeader
import com.spela.player.presentation.ui.feature.sharedsession.SharedSessionSaveItem
import com.spela.player.presentation.ui.components.InvitePlayerSheet
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
import com.spela.player.presentation.viewmodel.SharedSessionDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSessionDetailScreen(
    sharedSessionId: String,
    viewModel: SharedSessionDetailViewModel,
    onBack: () -> Unit,
    onPlay: (gameId: String, sharedSessionId: String) -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(sharedSessionId) {
        viewModel.onIntent(SharedSessionDetailIntent.LoadSharedSession(sharedSessionId))
        viewModel.onIntent(SharedSessionDetailIntent.LoadSaves(sharedSessionId))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = state.sharedSession?.name ?: "Shared Session",
                showBack = true,
                onBack = onBack,
            )

            if (state.isLoadingSharedSession && state.sharedSession == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading shared session...")
                }
            } else if (state.sharedSession != null) {
                val sharedSession = state.sharedSession ?: return
                val isRefreshing = state.isLoadingSharedSession || state.isLoadingSaves

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        viewModel.onIntent(SharedSessionDetailIntent.LoadSharedSession(sharedSessionId))
                        viewModel.onIntent(SharedSessionDetailIntent.LoadSaves(sharedSessionId))
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = SpSpacing.Default),
                    ) {
                        // Game info header
                        item {
                            SharedSessionHeader(
                                sharedSession = sharedSession,
                                isTakingTurn = state.isTakingTurn,
                                isReleasingTurn = state.isReleasingTurn,
                                hasActiveTurn = state.turnToken != null,
                                onTakeTurn = { viewModel.onIntent(SharedSessionDetailIntent.TakeTurn(sharedSessionId)) },
                                onReleaseTurn = { viewModel.onIntent(SharedSessionDetailIntent.ReleaseTurn(sharedSessionId)) },
                                onPlay = { onPlay(sharedSession.gameId, sharedSessionId) },
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
                            sharedSession.members,
                            key = { "member-${it.userId}" },
                        ) { member ->
                            MemberItem(
                                member = member,
                                isActive = member.userId == sharedSession.activeUserId,
                            )
                        }

                        // Invite section
                        item {
                            Spacer(Modifier.height(SpSpacing.Default))
                            InviteSection(
                                isInviting = state.isInviting,
                                onInvite = { username ->
                                    viewModel.onIntent(SharedSessionDetailIntent.InviteUser(sharedSessionId, username))
                                },
                                onShowInviteSheet = {
                                    viewModel.onIntent(SharedSessionDetailIntent.ShowInviteSheet)
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
                                SharedSessionSaveItem(
                                    save = save,
                                    isCopying = state.copyingSaveId == save.id,
                                    onCopyToGame = {
                                        viewModel.onIntent(
                                            SharedSessionDetailIntent.CopySaveToGame(sharedSessionId, save.id)
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
                    onAction = { viewModel.onIntent(SharedSessionDetailIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(SharedSessionDetailIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SpSnackbar(
            data = state.successMessage?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Success,
                )
            },
            onDismiss = { viewModel.onIntent(SharedSessionDetailIntent.DismissSuccess) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Invite player dialog
    if (state.showInviteSheet) {
        InvitePlayerSheet(
            searchQuery = state.inviteSearchQuery,
            onSearchQueryChange = { viewModel.onIntent(SharedSessionDetailIntent.UpdateInviteSearchQuery(it)) },
            searchResults = state.inviteSearchResults,
            searchTotal = state.inviteSearchTotal,
            searchPage = state.inviteSearchPage,
            recentPartners = state.recentPartners,
            isSearching = state.isSearchingUsers,
            isLoadingRecentPartners = state.isLoadingRecentPartners,
            invitingUsername = state.invitingUsername,
            invitedUsernames = state.invitedUsernames,
            onInvite = { username ->
                viewModel.onIntent(SharedSessionDetailIntent.InviteUser(sharedSessionId, username))
            },
            onPageChange = { viewModel.onIntent(SharedSessionDetailIntent.InviteSearchPage(it)) },
            onDismiss = { viewModel.onIntent(SharedSessionDetailIntent.HideInviteSheet) },
        )
    }
}
