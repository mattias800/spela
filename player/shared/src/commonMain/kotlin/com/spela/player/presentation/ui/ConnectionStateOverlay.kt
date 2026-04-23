package com.spela.player.presentation.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.spela.player.data.remote.ConnectionState
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.NavigationViewModel
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.components.SpAuthExpiredDialog
import com.spela.player.presentation.ui.components.SpDatabaseErrorScreen
import com.spela.player.presentation.ui.components.SpOfflineBanner
import com.spela.player.presentation.ui.components.SpServerWarningCard

/**
 * Extracted in #693 PR C. Owns the cascade of non-happy connection
 * states on top of whatever the app wants to show underneath:
 *
 *   DatabaseError  → full-screen takeover (content is hidden)
 *   AuthFailed     → dialog on top of content
 *   Offline        → muted banner above content
 *   ServerUnreach. → dismissible warning card above content
 *
 * The priority (DatabaseError > AuthFailed > ServerUnreachable > Offline)
 * matches the pre-refactor inline block in SpelaApp.
 *
 * Also keeps the two pieces of state that go with those UIs —
 * `serverWarningDismissed` resets whenever the state leaves
 * ServerUnreachable, and `hasBeenOffline` tracks the offline→online
 * transition so we can fire exactly one "Back online" snackbar via
 * the [onBackOnline] callback. SpelaApp chooses how to render that
 * snackbar (it shares a Snackbar host with other events).
 *
 * Declared as a `ColumnScope` extension and taking a
 * `ColumnScope.() -> Unit` content slot so the banner/card + the
 * screen router underneath all render into the caller's Column and
 * keep their `.weight(1f)` layout semantics.
 */
@Composable
fun ColumnScope.ConnectionStateOverlay(
    connectivityMonitor: ConnectivityMonitor,
    navigationViewModel: NavigationViewModel,
    onBackOnline: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state by connectivityMonitor.connectionState.collectAsState()
    var serverWarningDismissed by remember { mutableStateOf(false) }
    var hasBeenOffline by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is ConnectionState.Online) {
            if (hasBeenOffline) onBackOnline()
        } else if (!state.isConnected) {
            hasBeenOffline = true
        }
        if (state !is ConnectionState.ServerUnreachable) {
            serverWarningDismissed = false
        }
    }

    if (state is ConnectionState.DatabaseError) {
        SpDatabaseErrorScreen(
            message = (state as ConnectionState.DatabaseError).message,
            onResetApp = { navigationViewModel.resetDatabase() },
        )
        return
    }

    if (state is ConnectionState.AuthFailed) {
        SpAuthExpiredDialog(
            onSignIn = {
                connectivityMonitor.clearAuthFailure()
                navigationViewModel.onIntent(
                    NavigationIntent.NavigateTo(SpScreen.Login)
                )
            },
            onContinueOffline = {
                connectivityMonitor.clearAuthFailure()
            },
        )
    }

    SpOfflineBanner(connectionState = state)

    if (state is ConnectionState.ServerUnreachable && !serverWarningDismissed) {
        SpServerWarningCard(
            onCheckServerSettings = {
                navigationViewModel.onIntent(
                    NavigationIntent.NavigateTo(SpScreen.Settings)
                )
            },
            onDismiss = { serverWarningDismissed = true },
        )
    }

    content()
}
