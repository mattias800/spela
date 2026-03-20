package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun WildFeaturesSection(
    onSurpriseMe: () -> Unit,
    onWizardSelected: () -> Unit,
    modifier: Modifier = Modifier,
    includeTopSpacing: Boolean = true,
) {
    SpTitledSection(
        title = "Feeling Adventurous?",
        modifier = modifier,
        includeTopSpacing = includeTopSpacing,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            // I'm Feeling Lucky
            SpCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag("wild_lucky_button"),
                onClick = onSurpriseMe,
            ) {
                Row(
                    modifier = Modifier.padding(SpSpacing.Default),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Casino,
                        contentDescription = null,
                        tint = SpColor.Warning,
                        modifier = Modifier.size(SpSpacing.IconLarge),
                    )
                    Spacer(modifier = Modifier.width(SpSpacing.Medium))
                    Column {
                        Text(
                            text = "I'm Feeling Lucky",
                            color = SpColor.OnBackground,
                            style = SpTypography.LabelLarge,
                        )
                        Text(
                            text = "Random game",
                            color = SpColor.OnBackgroundSecondary,
                            style = SpTypography.BodySmall,
                        )
                    }
                }
            }

            // Decision Wizard
            SpCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag("wild_wizard_button"),
                onClick = onWizardSelected,
            ) {
                Row(
                    modifier = Modifier.padding(SpSpacing.Default),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        tint = SpColor.Primary,
                        modifier = Modifier.size(SpSpacing.IconLarge),
                    )
                    Spacer(modifier = Modifier.width(SpSpacing.Medium))
                    Column {
                        Text(
                            text = "Decision Wizard",
                            color = SpColor.OnBackground,
                            style = SpTypography.LabelLarge,
                        )
                        Text(
                            text = "3 questions",
                            color = SpColor.OnBackgroundSecondary,
                            style = SpTypography.BodySmall,
                        )
                    }
                }
            }
        }
    }
}
