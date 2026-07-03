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
import androidx.compose.foundation.layout.width
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
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * In-game Wii controller scheme picker (#1559). One radio row per
 * [WiiControlScheme]; picking a scheme commits (persist + live re-apply)
 * and closes. Same scrim/card structure as [InGameSlotPickerDialog].
 */
@Composable
fun InGameWiiControlSchemeDialog(
    currentScheme: WiiControlScheme,
    onSelect: (WiiControlScheme) -> Unit,
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
                .width(440.dp)
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
                modifier = Modifier.verticalScroll(rememberScrollState()),
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
            }
            Spacer(Modifier.height(SpSpacing.Medium))
            SpSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
            )
        }
    }
}
