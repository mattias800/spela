package com.spela.player.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.AchievementEventType
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

@Composable
fun AchievementPopup(
    event: AchievementEvent?,
    onDismiss: () -> Unit,
) {
    if (event != null) {
        LaunchedEffect(event) {
            delay(4000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = event != null,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
    ) {
        if (event != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
                contentAlignment = Alignment.TopCenter,
            ) {
                // Tap-to-dismiss: explicit interaction source + null indication
                // skips the ripple — the banner already animates out, a ripple
                // on top would feel double-stamped. Role.Button keeps the node
                // accessible to screen readers and gamepad navigation.
                val interactionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
                        .background(SpColor.PrimaryContainer)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onDismiss,
                        )
                        .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (event.type) {
                                AchievementEventType.ACHIEVEMENT_TRIGGERED -> "Achievement Unlocked!"
                                AchievementEventType.GAME_COMPLETED -> "Game Completed!"
                                AchievementEventType.LEADERBOARD_STARTED -> "Leaderboard Started"
                                AchievementEventType.LEADERBOARD_SUBMITTED -> "Leaderboard Submitted"
                                AchievementEventType.LEADERBOARD_FAILED -> "Leaderboard Failed"
                                else -> "Achievement"
                            },
                            style = SpTypography.LabelMedium,
                            color = SpColor.PrimaryLight,
                        )
                        if (event.title.isNotEmpty()) {
                            Text(
                                text = event.title,
                                style = SpTypography.TitleMedium,
                                color = SpColor.OnBackground,
                            )
                        }
                        if (event.description.isNotEmpty()) {
                            Text(
                                text = event.description,
                                style = SpTypography.BodySmall,
                                color = SpColor.OnBackgroundSecondary,
                            )
                        }
                    }
                    if (event.points > 0) {
                        Text(
                            text = "${event.points}",
                            style = SpTypography.HeadlineMedium,
                            color = SpColor.Warning,
                        )
                    }
                }
            }
        }
    }
}
