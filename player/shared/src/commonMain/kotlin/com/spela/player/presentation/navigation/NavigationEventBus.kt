package com.spela.player.presentation.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface NavigationEvent {
    data object NextSection : NavigationEvent
    data object PreviousSection : NavigationEvent
}

class NavigationEventBus {
    private val _events = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<NavigationEvent> = _events.asSharedFlow()

    fun emit(event: NavigationEvent) {
        _events.tryEmit(event)
    }
}
