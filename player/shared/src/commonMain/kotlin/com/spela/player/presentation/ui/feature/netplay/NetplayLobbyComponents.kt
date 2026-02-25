package com.spela.player.presentation.ui.feature.netplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.NetplaySession
import com.spela.player.domain.model.NetplaySessionStatus
import com.spela.player.presentation.ui.feature.library.getConsoleColor
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpConsoleChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun NetplayStatusChip(status: NetplaySessionStatus) {
    SpChip(
        text = when (status) {
            NetplaySessionStatus.WAITING -> "Waiting"
            NetplaySessionStatus.IN_PROGRESS -> "In Progress"
            NetplaySessionStatus.ENDED -> "Ended"
        },
        color = when (status) {
            NetplaySessionStatus.WAITING -> SpColor.Warning
            NetplaySessionStatus.IN_PROGRESS -> SpColor.Success
            NetplaySessionStatus.ENDED -> SpColor.OnBackgroundTertiary
        },
    )
}

@Composable
internal fun LobbyHeader(session: NetplaySession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpCoverArt(
            imageUrl = session.gameCoverUrl,
            contentDescription = "${session.gameTitle} cover",
            modifier = Modifier.width(80.dp),
            cornerRadius = SpSpacing.RadiusLarge,
            aspectRatio = session.coverAspectRatio,
        )
        Spacer(Modifier.width(SpSpacing.Default))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.gameTitle,
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (session.gameConsoleName.isNotEmpty()) {
                Spacer(Modifier.height(SpSpacing.XXSmall))
                SpConsoleChip(
                    consoleName = session.gameConsoleName,
                    consoleColor = getConsoleColor(session.gameConsoleName),
                )
            }
            Spacer(Modifier.height(SpSpacing.Small))
            NetplayStatusChip(status = session.status)
        }
    }
}

@Composable
internal fun InputDelaySection(
    inputDelay: Int,
    isHost: Boolean,
    isUpdating: Boolean,
    onChangeDelay: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
    ) {
        Text(
            text = "Input Delay",
            style = SpTypography.HeadlineLarge,
            color = SpColor.OnBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(SpSpacing.Small))

        SpCard(onGradient = true, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpSpacing.Default),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isHost) {
                    SpButton(
                        text = "",
                        onClick = { if (inputDelay > 1) onChangeDelay(inputDelay - 1) },
                        style = SpButtonStyle.Outlined,
                        enabled = inputDelay > 1 && !isUpdating,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "Decrease input delay",
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )

                    Text(
                        text = "$inputDelay frames",
                        style = SpTypography.TitleLarge,
                        color = SpColor.Primary,
                        modifier = Modifier.semantics {
                            contentDescription = "Input delay: $inputDelay frames"
                        },
                    )

                    SpButton(
                        text = "",
                        onClick = { if (inputDelay < 10) onChangeDelay(inputDelay + 1) },
                        style = SpButtonStyle.Outlined,
                        enabled = inputDelay < 10 && !isUpdating,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Increase input delay",
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                } else {
                    Text(
                        text = "Input Delay",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnCard,
                    )
                    Text(
                        text = "$inputDelay frames (set by host)",
                        style = SpTypography.TitleLarge,
                        color = SpColor.Primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(SpSpacing.XSmall))
        Text(
            text = "Higher delay = smoother online play. Lower delay = more responsive controls. Start with 3 and adjust if you notice stuttering.",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}
