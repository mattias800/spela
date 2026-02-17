package com.spela.player.presentation.state

import com.spela.player.domain.model.Challenge
import com.spela.player.domain.model.ChallengeAttempt
import com.spela.player.domain.model.ChallengeLeaderboardEntry

data class ChallengeListState(
    val challenges: List<Challenge> = emptyList(),
    val myChallenges: List<Challenge> = emptyList(),
    val gameChallenges: List<Challenge> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMyChallenges: Boolean = false,
    val isLoadingGameChallenges: Boolean = false,
    val error: String? = null,
)

data class ChallengeDetailState(
    val challenge: Challenge? = null,
    val leaderboard: List<ChallengeLeaderboardEntry> = emptyList(),
    val myAttempts: List<ChallengeAttempt> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingLeaderboard: Boolean = false,
    val hasMoreLeaderboard: Boolean = false,
    val leaderboardPage: Int = 1,
    val error: String? = null,
    val deleteSuccess: Boolean = false,
)
