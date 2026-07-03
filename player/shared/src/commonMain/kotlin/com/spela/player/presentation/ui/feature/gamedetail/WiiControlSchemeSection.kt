package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.WiiControlScheme
import com.spela.player.domain.model.WiiIrSource
import com.spela.player.presentation.ui.components.SpInfoCallout
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Per-game Wii controller picker on the game detail screen (#1559, #1560).
 * Rendered only for Wii games; both the controller scheme and the IR pointer
 * source persist device-locally and apply on the next launch (and live
 * in-game via the overlay's picker).
 */
@Composable
fun WiiControlSchemeSection(
    currentScheme: WiiControlScheme,
    currentIrSource: WiiIrSource,
    onSelectScheme: (WiiControlScheme) -> Unit,
    onSelectIrSource: (WiiIrSource) -> Unit,
) {
    SpTitledSection(
        title = "Wii Remote",
        icon = Icons.Filled.SettingsRemote,
    ) {
        Column(
            modifier = Modifier.testTag("wii-control-scheme-section"),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            Text(
                text = "Controller",
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            WiiControlScheme.entries.forEach { scheme ->
                SpRadioOption(
                    title = scheme.displayName,
                    description = scheme.description,
                    isSelected = scheme == currentScheme,
                    onClick = { onSelectScheme(scheme) },
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
            Spacer(Modifier.height(SpSpacing.Small))
            SpInfoCallout(
                title = "Limited motion support",
                text = "Shake works from a trigger, steering works with stick tilt, and pointer aiming works with touch. Full swing or sword controls need a real Wii Remote.",
                testTagName = "wii-motion-support-hint",
            )
        }
    }
}
