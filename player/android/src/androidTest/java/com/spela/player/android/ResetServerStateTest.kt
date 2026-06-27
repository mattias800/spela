package com.spela.player.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused integration test for resetServerState(). Runs against the E2E
 * server prepared by run-e2e.sh/CI. Deliberately does NOT extend BaseE2ETest
 * — the whole point is to exercise the reset call in isolation so a
 * future regression is easy to diagnose without the base-class setup
 * noise.
 */
@RunWith(AndroidJUnit4::class)
class ResetServerStateTest {

    @Test
    fun resetReturnsSuccessfully() {
        // If this throws, the error message already identifies the cause
        // (test mode off, port forwarding missing, server down, etc.).
        // Calling twice in a row proves idempotency too.
        resetServerState()
        resetServerState()
    }
}
