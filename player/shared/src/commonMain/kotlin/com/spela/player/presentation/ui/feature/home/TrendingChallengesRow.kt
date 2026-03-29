package com.spela.player.presentation.ui.feature.home

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Challenge
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.challenge.SpChallengeCard
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
internal fun TrendingChallengesRow(
    challenges: List<Challenge>,
    onChallengeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        modifier = modifier,
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
