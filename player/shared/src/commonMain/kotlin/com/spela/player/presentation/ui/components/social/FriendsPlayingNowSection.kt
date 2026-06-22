package com.spela.player.presentation.ui.components.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.FriendPresence
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

// Max presence rows rendered at once — keeps the fixed-height section from
// crowding out the console chips + game grid below it.
private const val MAX_PRESENCE_ROWS = 6

/**
 * "Playing now across connected servers" — a self-contained, movable widget that
 * lists players active on connected (federated) servers right now. Pass it the
 * (already remote-filtered) presence list + a tap handler; it renders the whole
 * titled section, or nothing when no one is playing. No other dependencies, so
 * it can be dropped onto any screen as we iterate on placement.
 *
 * Rows are gamepad-focusable and participate in the host's focus-restore scope
 * (isDefault = false — the host keeps its own default-focus element; presence
 * loads asynchronously, so this section is absent during the entry focus-settle
 * window and never competes for the initial focus).
 */
@Composable
fun FriendsPlayingNowSection(
    presence: List<FriendPresence>,
    onGameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (presence.isEmpty()) return

    // Cap the rows so a large mesh can't push the console chips + game grid off
    // screen (the section is fixed-height above the scrolling grid). Realistic
    // counts are small; this is just a guard.
    val shown = presence.take(MAX_PRESENCE_ROWS)

    SpTitledSection(
        title = "Playing now across connected servers",
        icon = Icons.Default.Gamepad,
        modifier = modifier.testTag("friends_playing_now"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
            shown.forEach { p ->
                PresenceRow(
                    presence = p,
                    onClick = { onGameSelected(p.gameKey) },
                    modifier = Modifier.focusRestoreItem(
                        key = "presence_${p.serverName}_${p.username}_${p.gameKey}",
                        isDefault = false,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PresenceRow(
    presence: FriendPresence,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val serverLabel = presence.serverName.ifEmpty { "a connected server" }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
            .semantics {
                contentDescription =
                    "${presence.username} playing ${presence.gameTitle} on $serverLabel"
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .gamepadFocusable(
                shape = RoundedCornerShape(SpSpacing.RadiusLarge),
                interactionSource = interactionSource,
                addFocusable = false,
            )
            .testTag("friends_playing_now_row")
            .padding(SpSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Default),
    ) {
        Box(modifier = Modifier.size(40.dp)) {
            SpAvatar(username = presence.username, avatarUrl = null, size = 40.dp)
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .clip(CircleShape)
                    .background(SpColor.Background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(SpColor.Success),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = presence.username,
                style = SpTypography.LabelLarge,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${presence.gameTitle} · on $serverLabel",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
