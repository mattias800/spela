package com.spela.player.presentation.intent

import com.spela.player.domain.model.TopListTab

sealed interface TopListsIntent {
    data object LoadTopLists : TopListsIntent
    data class SelectTab(val tab: TopListTab) : TopListsIntent
    data object DismissError : TopListsIntent
}
