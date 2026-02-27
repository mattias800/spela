package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.data.remote.ConnectionState
import com.spela.player.data.remote.SyncState
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpSyncStatusIndicator(
    syncState: SyncState,
    connectionState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val pendingTotal = syncState.pendingSaveStates + syncState.pendingSaveData

    val (status, label) = when (connectionState) {
        is ConnectionState.Online -> ConnectionStatus.CONNECTED to "Online"
        is ConnectionState.Offline -> ConnectionStatus.DISCONNECTED to "Offline"
        is ConnectionState.ServerUnreachable -> ConnectionStatus.DISCONNECTED to "Server Unreachable"
        is ConnectionState.AuthFailed -> ConnectionStatus.DISCONNECTED to "Auth Expired"
        is ConnectionState.DatabaseError -> ConnectionStatus.DISCONNECTED to "Database Error"
    }

    Column(
        modifier = modifier
            .padding(vertical = SpSpacing.Small)
            .semantics { contentDescription = "Sync status" },
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            SpConnectionBadge(
                status = status,
                label = label,
            )

            if (syncState.isSyncing) {
                SyncingText()
            }
        }

        if (pendingTotal > 0) {
            Text(
                text = "$pendingTotal pending sync${if (pendingTotal != 1) "s" else ""}",
                style = SpTypography.LabelSmall,
                color = SpColor.Warning,
            )
        }

        syncState.lastSyncedAt?.let { lastSynced ->
            Text(
                text = "Last synced: ${lastSynced.toString().take(19).replace('T', ' ')}",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundSecondary,
            )
        }
    }
}

@Composable
private fun SyncingText() {
    val alpha = if (LocalAnimationsEnabled.current) {
        val transition = rememberInfiniteTransition(label = "syncing")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "syncingAlpha",
        )
        animatedAlpha
    } else {
        0.75f
    }

    Text(
        text = "Syncing...",
        style = SpTypography.LabelSmall,
        color = SpColor.Primary,
        modifier = Modifier.alpha(alpha),
    )
}
