package com.spela.player.presentation.ui.components.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.ChallengeDifficulty
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpDifficultyChip(
    difficulty: ChallengeDifficulty,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor) = when (difficulty) {
        ChallengeDifficulty.EASY -> SpColor.Success.copy(alpha = 0.15f) to SpColor.Success
        ChallengeDifficulty.MEDIUM -> SpColor.Warning.copy(alpha = 0.15f) to SpColor.Warning
        ChallengeDifficulty.HARD -> SpColor.Error.copy(alpha = 0.15f) to SpColor.Error
    }

    Text(
        text = difficulty.displayName,
        style = SpTypography.LabelSmall,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
            .background(backgroundColor)
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XXSmall)
            .semantics { contentDescription = "${difficulty.displayName} difficulty" },
    )
}
