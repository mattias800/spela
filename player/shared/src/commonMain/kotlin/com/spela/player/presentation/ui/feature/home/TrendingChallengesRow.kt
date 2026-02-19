package com.spela.player.presentation.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Challenge
import com.spela.player.presentation.ui.components.challenge.SpChallengeCard
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
internal fun TrendingChallengesRow(
    challenges: List<Challenge>,
    onChallengeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(challenges, key = { it.id }) { challenge ->
            SpChallengeCard(
                challenge = challenge,
                onClick = { onChallengeSelected(challenge.id) },
                modifier = Modifier.width(220.dp),
            )
        }
    }
}
