package com.spela.player.data.remote

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConnectivityMonitor(
    private val apiClient: SpelaApiClient,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _onReconnect = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onReconnect: SharedFlow<Unit> = _onReconnect.asSharedFlow()

    private companion object {
        const val POLL_INTERVAL_ONLINE_MS = 30_000L
        const val POLL_INTERVAL_OFFLINE_MS = 10_000L
    }

    fun start() {
        scope.launch(dispatchers.io) {
            while (true) {
                val wasOnline = _isOnline.value
                val reachable = runCatching { apiClient.healthCheck() }.getOrDefault(false)

                if (reachable && !wasOnline) {
                    _isOnline.value = true
                    _onReconnect.tryEmit(Unit)
                } else if (!reachable && wasOnline) {
                    _isOnline.value = false
                }

                val interval = if (_isOnline.value) POLL_INTERVAL_ONLINE_MS else POLL_INTERVAL_OFFLINE_MS
                delay(interval)
            }
        }
    }

    fun reportOffline() {
        _isOnline.value = false
    }

    fun reportOnline() {
        val wasOffline = !_isOnline.value
        _isOnline.value = true
        if (wasOffline) {
            _onReconnect.tryEmit(Unit)
        }
    }
}
