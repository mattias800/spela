package com.spela.player.presentation.ui.components.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ChallengeType
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpChallengeTypeChip(
    type: ChallengeType,
    modifier: Modifier = Modifier,
) {
    val icon = when (type) {
        ChallengeType.COMPLETION -> Icons.Filled.CheckCircle
        ChallengeType.SPEEDRUN -> Icons.Filled.Timer
        ChallengeType.SURVIVAL -> Icons.Filled.Shield
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
            .background(SpColor.SurfaceVariant)
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XXSmall)
            .semantics { contentDescription = "${type.displayName} challenge" },
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpColor.OnBackgroundTertiary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = type.displayName,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}
