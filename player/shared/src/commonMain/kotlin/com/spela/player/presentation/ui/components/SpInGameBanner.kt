package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * DESIGN component (Layer 1) — a thin, top-aligned informational
 * banner with an icon, text, and up to two trailing action buttons.
 * Designed to sit over an in-game surface (the emulator rendering
 * area) where full-sheet dialogs would cover gameplay the user is
 * actively inspecting.
 *
 * First caller is the #672 rehearsal flow: while the user is trying
 * their save with a new core, the banner stays visible at the top
 * with a trailing "Did this work?" button and an optional "Switch to
 * older version for comparison" secondary action. It's also used for
 * the 2-second "Locked to version v-a3f9" confirmation that follows a
 * lock decision.
 *
 * Not the same thing as [SpOfflineBanner] — that one is bottom-aligned,
 * full-width, no actions, lives outside the emulation surface. We add
 * this as a sibling rather than extending `SpOfflineBanner` because
 * the placement and affordance story differ too much. If a third
 * in-game banner pattern appears, unify.
 *
 * Accepts [Modifier] so the parent (`EmulationScreen`) can position
 * it relative to the emulator surface; internal padding / spacing are
 * the component's own.
 */
@Composable
fun SpInGameBanner(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    primaryAction: SpDecisionAction? = null,
    secondaryAction: SpDecisionAction? = null,
    testTagName: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(SpColor.SurfaceElevated.copy(alpha = 0.92f))
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
            .then(
                if (testTagName != null) Modifier.testTag(testTagName) else Modifier,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpColor.OnBackgroundSecondary,
        )
        Spacer(Modifier.width(SpSpacing.Small))
        Text(
            text = text,
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.weight(1f),
        )
        if (secondaryAction != null) {
            Spacer(Modifier.width(SpSpacing.Small))
            SpButton(
                text = secondaryAction.label,
                onClick = secondaryAction.onClick,
                style = SpButtonStyle.Ghost,
                modifier = Modifier.testTag("sp-banner-secondary"),
            )
        }
        if (primaryAction != null) {
            Spacer(Modifier.width(SpSpacing.Small))
            SpButton(
                text = primaryAction.label,
                onClick = primaryAction.onClick,
                style = SpButtonStyle.Secondary,
                modifier = Modifier.testTag("sp-banner-primary"),
            )
        }
    }
}
