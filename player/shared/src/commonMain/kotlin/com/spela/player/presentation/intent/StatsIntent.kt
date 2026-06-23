package com.spela.player.presentation.intent

import com.spela.player.presentation.state.StatScope

sealed interface StatsIntent {
    data object LoadStats : StatsIntent
    data object DismissError : StatsIntent
    data class SetMostPlayedScope(val scope: StatScope) : StatsIntent
    data class SetActivePlayersScope(val scope: StatScope) : StatsIntent
    data class SetAchieversScope(val scope: StatScope) : StatsIntent
}
