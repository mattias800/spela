package com.spela.player.presentation.ui.feature.sharedsession

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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionMember
import com.spela.player.domain.model.SharedSessionSave
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.components.social.formatRelativeTime
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun SharedSessionHeader(
    sharedSession: SharedSessionDetail,
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
                imageUrl = sharedSession.gameCoverUrl,
                contentDescription = "${sharedSession.gameTitle} cover",
                modifier = Modifier.size(width = 80.dp, height = 107.dp),
                cornerRadius = SpSpacing.RadiusLarge,
            )
            Spacer(Modifier.width(SpSpacing.Default))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sharedSession.gameTitle,
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sharedSession.gameConsoleName.isNotEmpty()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = sharedSession.gameConsoleName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
                Spacer(Modifier.height(SpSpacing.Small))
                Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                    SharedSessionStatusChip(status = sharedSession.status)
                    SpChip(text = "${sharedSession.memberCount} members")
                }
            }
        }

        if (sharedSession.description.isNotEmpty()) {
            Spacer(Modifier.height(SpSpacing.Default))
            Text(
                text = sharedSession.description,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )
        }

        Spacer(Modifier.height(SpSpacing.Default))

        // Turn controls
        val isMyTurn = hasActiveTurn
        val turnHeld = sharedSession.activeUserId != null
        val activeUser = sharedSession.members.find { it.userId == sharedSession.activeUserId }

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
internal fun SharedSessionStatusChip(status: String) {
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
    member: SharedSessionMember,
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
    onShowInviteSheet: () -> Unit = {},
) {
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
        SpButton(
            text = "Invite Player",
            onClick = onShowInviteSheet,
        )
    }
}

@Composable
internal fun SharedSessionSaveItem(
    save: SharedSessionSave,
    isCopying: Boolean = false,
    onCopyToGame: (() -> Unit)? = null,
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
                    text = buildString {
                        append("by ${save.username}")
                        val age = formatRelativeTime(save.createdAt)
                        if (age.isNotEmpty()) append(" · $age")
                    },
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
            if (save.isAuto) {
                SpChip(text = "Auto", color = SpColor.Primary)
            }
            if (onCopyToGame != null) {
                if (isCopying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = SpColor.Primary,
                    )
                } else {
                    IconButton(
                        onClick = onCopyToGame,
                        modifier = Modifier.semantics {
                            contentDescription = "Copy ${save.name} to your library"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }
    }
}
