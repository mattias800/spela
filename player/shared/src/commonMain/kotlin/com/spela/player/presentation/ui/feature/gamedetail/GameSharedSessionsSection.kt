package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import com.spela.player.domain.model.SharedSession
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun GameSharedSessionsSection(
    sharedSessions: List<SharedSession>,
    isLoading: Boolean,
    onSharedSessionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hide section entirely if no shared sessions and not loading
    if (sharedSessions.isEmpty() && !isLoading) return

    SpTitledSection(
        title = "Active Shared Sessions",
        icon = Icons.Outlined.Repeat,
        modifier = modifier.testTag("game_shared_sessions_section"),
        titleTrailing = if (sharedSessions.isNotEmpty()) {
            {
                Text(
                    text = "(${sharedSessions.size})",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        } else null,
    ) {
        sharedSessions.forEach { sharedSession ->
            SharedSessionItem(
                sharedSession = sharedSession,
                onClick = { onSharedSessionClick(sharedSession.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.XXSmall),
            )
        }
    }
}

@Composable
private fun SharedSessionItem(
    sharedSession: SharedSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpInnerCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("shared_session_item_${sharedSession.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sharedSession.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    Text(
                        text = sharedSession.ownerUsername,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                    Text(
                        text = "${sharedSession.memberCount} members",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        modifier = Modifier.semantics {
                            contentDescription = "${sharedSession.memberCount} members in ${sharedSession.name}"
                        },
                    )
                }
            }

            Spacer(Modifier.width(SpSpacing.Small))

            val statusColor = when (sharedSession.status) {
                "active" -> SpColor.Success
                "paused" -> SpColor.Warning
                else -> SpColor.OnBackgroundTertiary
            }

            SpChip(
                text = sharedSession.status.replaceFirstChar { it.uppercase() },
                color = statusColor,
            )
        }
    }
}
