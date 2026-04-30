package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * "Save with name…" dialog reachable from the medium-tier slot picker
 * (#830). The slot grid stays the primary save metaphor; this is the
 * power-user escape hatch for deliberate markers ("Before final
 * boss") on top of the rolling slot saves.
 *
 * Empty submission is rejected — a named save with no name would
 * just produce another row labelled "Manual Save", which the slot
 * picker already provides. Cancel and the scrim both dismiss
 * without saving.
 */
@Composable
fun InGameNamedSaveDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val canConfirm = name.isNotBlank()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .testTag("in-game-named-save-dialog"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Save with name",
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
            )
            Text(
                text = "Slot saves keep rolling on top of each other. Use this for a deliberate marker you want to keep.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = SpSpacing.Small)
                    .fillMaxWidth(),
            )
            SpTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Before the final boss",
                modifier = Modifier
                    .padding(top = SpSpacing.Medium)
                    .fillMaxWidth()
                    .testTag("named-save-input"),
            )
            Row(
                modifier = Modifier
                    .padding(top = SpSpacing.Medium)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    SpSpacing.Small,
                    Alignment.CenterHorizontally,
                ),
            ) {
                SpSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.testTag("named-save-cancel"),
                )
                SpButton(
                    text = "Save",
                    onClick = { onConfirm(name) },
                    enabled = canConfirm,
                    modifier = Modifier.testTag("named-save-confirm"),
                )
            }
        }
    }
}
