package com.spela.player.presentation.intent

sealed interface StatsIntent {
    data object LoadStats : StatsIntent
    data object DismissError : StatsIntent
}
