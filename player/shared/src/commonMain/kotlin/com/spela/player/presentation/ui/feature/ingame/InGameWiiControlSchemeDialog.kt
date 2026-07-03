package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.WiiControlScheme
import com.spela.player.domain.model.WiiIrSource
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * In-game Wii controller picker (#1559, #1560). A radio group for the
 * controller [WiiControlScheme] and one for the IR pointer [WiiIrSource];
 * picking either commits (persist + live-apply) and closes. Same scrim/card
 * structure as [InGameSlotPickerDialog].
 */
@Composable
fun InGameWiiControlSchemeDialog(
    currentScheme: WiiControlScheme,
    currentIrSource: WiiIrSource,
    onSelect: (WiiControlScheme) -> Unit,
    onSelectIrSource: (WiiIrSource) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .testTag("in-game-wii-scheme-picker"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // Prevent click-through to scrim.
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Wii Remote setup",
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
            )
            Spacer(Modifier.height(SpSpacing.Medium))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                WiiControlScheme.entries.forEach { scheme ->
                    SpRadioOption(
                        title = scheme.displayName,
                        description = scheme.description,
                        isSelected = scheme == currentScheme,
                        onClick = { onSelect(scheme) },
                        modifier = Modifier.testTag("wii-scheme-option-${scheme.storageId}"),
                    )
                }
                Spacer(Modifier.height(SpSpacing.Small))
                Text(
                    text = "Pointer",
                    style = SpTypography.LabelMedium,
                    color = SpColor.OnBackgroundSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                WiiIrSource.entries.forEach { source ->
                    SpRadioOption(
                        title = source.displayName,
                        description = source.description,
                        isSelected = source == currentIrSource,
                        onClick = { onSelectIrSource(source) },
                        modifier = Modifier.testTag("wii-ir-source-option-${source.storageId}"),
                    )
                }
            }
            Spacer(Modifier.height(SpSpacing.Medium))
            SpSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
            )
        }
    }
}
