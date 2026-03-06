package com.spela.player.presentation.intent

sealed interface TopListsIntent {
    data object LoadTopLists : TopListsIntent
    data object DismissError : TopListsIntent
}
