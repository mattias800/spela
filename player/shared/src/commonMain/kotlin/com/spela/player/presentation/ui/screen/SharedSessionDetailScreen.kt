package com.spela.player.presentation.ui.screen

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
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
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SharedSessionDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSessionDetailScreen(
    sharedSessionId: String,
    viewModel: SharedSessionDetailViewModel,
    onBack: () -> Unit,
    onPlay: (gameId: String, sharedSessionId: String) -> Unit,
    onNavigateToSession: ((String) -> Unit)? = null,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sharedSessionId) {
        viewModel.onIntent(SharedSessionDetailIntent.LoadSharedSession(sharedSessionId))
        viewModel.onIntent(SharedSessionDetailIntent.LoadSaves(sharedSessionId))
    }

    LaunchedEffect(state.clonedSessionId) {
        val newId = state.clonedSessionId
        if (newId != null) {
            onNavigateToSession?.invoke(newId)
            viewModel.onIntent(SharedSessionDetailIntent.ClearCloneNavigation)
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
                    title = state.sharedSession?.name ?: "Shared Session",
                    showBack = true,
                    onBack = onBack,
                    actions = {
                        // Clone action — only meaningful once the shared
                        // session has been played at least once (we need
                        // a backing GameSession to clone from). Hide
                        // entirely when there's nothing to clone.
                        val backing = state.sharedSession?.backingGameSessionId
                        if (backing != null) {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("shared_session_more_menu"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Shared session actions",
                                    tint = SpColor.OnBackground,
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clone to my library", style = SpTypography.BodyMedium) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showCloneDialog = true
                                    },
                                    modifier = Modifier.testTag("shared_session_clone_menu_item"),
                                )
                            }
                        }
                    },
                )
            }

            if (state.isLoadingSharedSession && state.sharedSession == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading shared session...")
                }
            } else if (state.sharedSession != null) {
                val sharedSession = state.sharedSession!!
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
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
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
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small),
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
                                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
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
                                    modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall),
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

    // Clone-to-my-library dialog — opened from the top-bar `…` menu.
    // Mirrors the rename dialog pattern (editable pre-filled name +
    // Cancel/Clone buttons). Only renders when the shared session has
    // a backing GameSessionID — guarded by the menu item above, but
    // we also defensively re-check here.
    if (showCloneDialog) {
        val backing = state.sharedSession?.backingGameSessionId
        val sourceName = state.sharedSession?.name ?: ""
        if (backing == null) {
            // The backing id vanished between menu open and dialog
            // render (rare). Just close the dialog.
            showCloneDialog = false
        } else {
            var cloneName by remember(backing) { mutableStateOf("$sourceName (Copy)") }
            AlertDialog(
                onDismissRequest = { showCloneDialog = false },
                title = { Text("Clone to my library") },
                text = {
                    OutlinedTextField(
                        value = cloneName,
                        onValueChange = { cloneName = it },
                        label = { Text("New Session Name") },
                        singleLine = true,
                        modifier = Modifier.testTag("shared_session_clone_input"),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (cloneName.isNotBlank()) {
                                viewModel.onIntent(
                                    SharedSessionDetailIntent.CloneToMyLibrary(
                                        backingGameSessionId = backing,
                                        name = cloneName.trim(),
                                    )
                                )
                                showCloneDialog = false
                            }
                        },
                        modifier = Modifier.testTag("shared_session_clone_confirm"),
                    ) {
                        Text("Clone")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCloneDialog = false }) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("shared_session_clone_dialog"),
            )
        }
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
