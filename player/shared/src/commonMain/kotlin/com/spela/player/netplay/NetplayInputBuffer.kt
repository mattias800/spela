package com.spela.player.netplay

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Thread-safe ring buffer for netplay frame inputs.
 *
 * The emulation thread writes local inputs and reads combined inputs,
 * while the network thread writes remote inputs. A mutex protects
 * concurrent access to the internal storage.
 */
class NetplayInputBuffer(private val bufferSize: Int = 256) {
    private val mutex = Mutex()
    private val buffer = LinkedHashMap<Long, MutableMap<Int, InputState>>()

    /**
     * Store local player input for a given frame and port.
     */
    suspend fun setLocalInput(frame: Long, port: Int, input: InputState) {
        mutex.withLock {
            buffer.getOrPut(frame) { mutableMapOf() }[port] = input
            evictOldEntries()
        }
    }

    /**
     * Store remote player input for a given frame and port.
     */
    suspend fun setRemoteInput(frame: Long, port: Int, input: InputState) {
        mutex.withLock {
            buffer.getOrPut(frame) { mutableMapOf() }[port] = input
            evictOldEntries()
        }
    }

    /**
     * Get all player inputs for a given frame.
     * Returns null if no inputs exist for the frame.
     */
    suspend fun getInputsForFrame(frame: Long): Map<Int, InputState>? {
        mutex.withLock {
            return buffer[frame]?.toMap()
        }
    }

    /**
     * Check if all player inputs are present for a given frame.
     */
    suspend fun hasAllInputsForFrame(frame: Long, playerCount: Int): Boolean {
        mutex.withLock {
            val frameInputs = buffer[frame] ?: return false
            return frameInputs.size >= playerCount
        }
    }

    /**
     * Clear all buffered inputs.
     */
    suspend fun clear() {
        mutex.withLock {
            buffer.clear()
        }
    }

    /**
     * Wait for all player inputs to arrive for a given frame, with timeout.
     * Returns the inputs map if all arrived in time, null otherwise.
     */
    suspend fun awaitInputsForFrame(
        frame: Long,
        playerCount: Int,
        timeoutMs: Long = 100,
    ): Map<Int, InputState>? {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                if (hasAllInputsForFrame(frame, playerCount)) {
                    return@withTimeoutOrNull getInputsForFrame(frame)
                }
                // Yield to allow network thread to write inputs
                kotlinx.coroutines.delay(1)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }

    /**
     * Evict oldest entries when buffer exceeds capacity.
     * Must be called while holding the mutex.
     */
    private fun evictOldEntries() {
        while (buffer.size > bufferSize) {
            val oldestKey = buffer.keys.firstOrNull() ?: break
            buffer.remove(oldestKey)
        }
    }
}
