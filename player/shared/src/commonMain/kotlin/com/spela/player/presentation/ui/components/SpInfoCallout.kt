package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * DESIGN component for short, non-blocking informational notes inside sections.
 *
 * Does not add outer spacing; parent layouts decide where the callout sits.
 */
@Composable
fun SpInfoCallout(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    testTagName: String? = null,
) {
    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SpColor.Accent.copy(alpha = 0.10f))
            .border(1.dp, SpColor.Accent.copy(alpha = 0.28f), shape)
            .padding(SpSpacing.Default)
            .then(if (testTagName != null) Modifier.testTag(testTagName) else Modifier),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpColor.Accent,
            modifier = Modifier.size(SpSpacing.IconDefault),
        )
        Spacer(Modifier.width(SpSpacing.Medium))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
        ) {
            Text(
                text = title,
                style = SpTypography.TitleMedium,
                color = SpColor.OnBackground,
            )
            Text(
                text = text,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundSecondary,
            )
        }
    }
}
