package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.presentation.state.KeyMappingState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun GameControlsSection(
    gameId: String,
    hasGameOverride: Boolean,
    onEnableOverride: () -> Unit,
    onClearOverride: () -> Unit,
    onEditMapping: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("game_controls_section")) {
        Text(
            text = "Controls",
            style = SpTypography.HeadlineSmall,
            color = SpColor.OnBackground,
        )

        Spacer(Modifier.height(SpSpacing.Small))

        SpCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                if (hasGameOverride) {
                    Text(
                        text = "This game has a custom key mapping.",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundSecondary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        SpButton(
                            text = "Edit",
                            onClick = onEditMapping,
                            style = SpButtonStyle.Outlined,
                        )
                        SpButton(
                            text = "Remove Override",
                            onClick = onClearOverride,
                            style = SpButtonStyle.Ghost,
                        )
                    }
                } else {
                    Text(
                        text = "Using default controller mapping.",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundTertiary,
                    )
                    SpButton(
                        text = "Set Custom Controls",
                        onClick = onEnableOverride,
                        style = SpButtonStyle.Outlined,
                    )
                }
            }
        }
    }
}
