package com.spela.player.presentation.ui.feature.relay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.RelayDetail
import com.spela.player.domain.model.RelayMember
import com.spela.player.domain.model.RelaySave
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun RelayHeader(
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
                cornerRadius = SpSpacing.RadiusLarge,
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
                    RelayStatusChip(status = relay.status)
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
                onGradient = true,
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
                    enabled = !isReleasingTurn,
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
                enabled = !isTakingTurn,
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
internal fun RelayStatusChip(status: String) {
    SpChip(
        text = status.replaceFirstChar { it.uppercase() },
        color = when (status) {
            "active" -> SpColor.Success
            "paused" -> SpColor.Warning
            else -> SpColor.OnBackgroundTertiary
        },
    )
}

@Composable
internal fun MemberItem(
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
        // Avatar
        SpAvatar(
            username = member.username,
            avatarUrl = member.avatarUrl,
            size = 40.dp,
        )
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
internal fun InviteSection(
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
internal fun RelaySaveItem(
    save: RelaySave,
) {
    SpCard(
        onGradient = true,
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
