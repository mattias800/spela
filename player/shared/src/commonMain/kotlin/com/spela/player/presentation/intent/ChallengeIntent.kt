package com.spela.player.presentation.intent

sealed interface ChallengeIntent {
    data class LoadChallenges(
        val gameId: String? = null,
        val sort: String? = null,
    ) : ChallengeIntent
    data class LoadGameChallenges(val gameId: String) : ChallengeIntent
    data object LoadMyChallenges : ChallengeIntent
    data class LoadChallengeDetail(val challengeId: String) : ChallengeIntent
    data class LoadLeaderboard(val challengeId: String) : ChallengeIntent
    data class LoadMoreLeaderboard(val challengeId: String) : ChallengeIntent
    data class DeleteChallenge(val challengeId: String) : ChallengeIntent
    data object DismissError : ChallengeIntent
}
