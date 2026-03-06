package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.Cheat
import com.spela.player.presentation.ui.components.SpSwitch
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun CheatsSection(
    cheats: List<Cheat>,
    isLoading: Boolean,
    onToggle: (cheatId: String, enabled: Boolean) -> Unit,
    onDisableAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cheats.isEmpty() && !isLoading) return

    val enabledCount = cheats.count { it.enabled }

    SpTitledSection(
        title = "Cheats",
        modifier = modifier.testTag("cheats_section"),
        icon = Icons.Outlined.Code,
        titleTrailing = {
            if (enabledCount > 0) {
                Text(
                    text = "$enabledCount active",
                    style = SpTypography.LabelMedium,
                    color = SpColor.Primary,
                )
            }
        },
    ) {
        if (isLoading) {
            Text(
                text = "Loading cheats\u2026",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )
        } else {
            Column {
                cheats.forEach { cheat ->
                    SpInnerCard(modifier = Modifier.padding(vertical = SpSpacing.XSmall)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
                                .semantics { contentDescription = "Cheat: ${cheat.description}" },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = cheat.description,
                                style = SpTypography.BodyMedium,
                                color = SpColor.OnBackground,
                                modifier = Modifier.weight(1f).padding(end = SpSpacing.Small),
                            )
                            SpSwitch(
                                checked = cheat.enabled,
                                onCheckedChange = { enabled -> onToggle(cheat.id, enabled) },
                            )
                        }
                    }
                }

                if (enabledCount > 0) {
                    SpButton(
                        text = "Disable All",
                        onClick = onDisableAll,
                        style = SpButtonStyle.Ghost,
                        modifier = Modifier.fillMaxWidth().padding(top = SpSpacing.Small),
                    )
                }
            }
        }
    }
}
