package com.spela.player.presentation.intent

import com.spela.player.presentation.state.ChallengeTab

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
    data class SelectTab(val tab: ChallengeTab) : ChallengeIntent
    data class FilterByConsole(val consoleId: String?) : ChallengeIntent
    data class FilterByDifficulty(val difficulty: String?) : ChallengeIntent
    data object LoadConsoles : ChallengeIntent
}
