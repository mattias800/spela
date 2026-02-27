package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpServerWarningCard(
    onCheckServerSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
            .background(SpColor.WarningContainer)
            .padding(SpSpacing.Default)
            .semantics { contentDescription = "Server unreachable warning" },
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        Text(
            text = "Can't reach your server",
            style = SpTypography.TitleSmall,
            color = SpColor.Warning,
        )
        Text(
            text = "Your server appears to be down or unreachable. You can continue using cached data.",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackground,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            SpButton(
                text = "Check Server Settings",
                onClick = onCheckServerSettings,
            )
            SpButton(
                text = "Dismiss",
                onClick = onDismiss,
            )
        }
    }
}
