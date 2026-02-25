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
import com.spela.player.domain.model.Relay
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun GameRelaysSection(
    relays: List<Relay>,
    isLoading: Boolean,
    onRelayClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hide section entirely if no relays and not loading
    if (relays.isEmpty() && !isLoading) return

    SpTitledSection(
        title = "Active Relays",
        icon = Icons.Outlined.Repeat,
        modifier = modifier.testTag("game_relays_section"),
        titleTrailing = if (relays.isNotEmpty()) {
            {
                Text(
                    text = "(${relays.size})",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        } else null,
    ) {
        relays.forEach { relay ->
            RelayItem(
                relay = relay,
                onClick = { onRelayClick(relay.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.XXSmall),
            )
        }
    }
}

@Composable
private fun RelayItem(
    relay: Relay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpInnerCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("relay_item_${relay.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relay.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    Text(
                        text = relay.ownerUsername,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                    Text(
                        text = "${relay.memberCount} members",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        modifier = Modifier.semantics {
                            contentDescription = "${relay.memberCount} members in ${relay.name}"
                        },
                    )
                }
            }

            Spacer(Modifier.width(SpSpacing.Small))

            val statusColor = when (relay.status) {
                "active" -> SpColor.Success
                "paused" -> SpColor.Warning
                else -> SpColor.OnBackgroundTertiary
            }

            SpChip(
                text = relay.status.replaceFirstChar { it.uppercase() },
                color = statusColor,
            )
        }
    }
}
