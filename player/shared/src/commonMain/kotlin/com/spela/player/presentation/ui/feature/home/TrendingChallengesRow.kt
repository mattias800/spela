package com.spela.player.presentation.ui.feature.home

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.focusRequester
import com.spela.player.domain.model.Challenge
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.challenge.SpChallengeCard

@Composable
internal fun TrendingChallengesRow(
    challenges: List<Challenge>,
    onChallengeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        itemCount = challenges.size,
        modifier = modifier,
    ) { index, focusRequester ->
        val challenge = challenges[index]
        SpChallengeCard(
            challenge = challenge,
            onClick = { onChallengeSelected(challenge.id) },
            modifier = Modifier
                .focusRequester(focusRequester)
                .width(220.dp),
        )
    }
}
