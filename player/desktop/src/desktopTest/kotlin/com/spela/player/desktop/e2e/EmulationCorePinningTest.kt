package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Desktop E2E tests for #555 Phase 3 — session-to-core pinning.
 *
 * A session's `pinnedCoreSha256` drives the core download path:
 * - When set and the server still has that build: emulation uses the
 *   version-pinned binary.
 * - When set but the server has pruned that build: the player falls
 *   back to the latest core and surfaces a non-blocking warning
 *   (`EmulationState.coreVersionWarning`).
 *
 * Both branches are exercised through the full UI (Play → prepare →
 * load core) so we verify the PrepareGameUseCase + EmulationViewModel
 * wiring end-to-end, not just the use case in isolation.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class EmulationCorePinningTest {

    private fun createHarnessWithGameReady(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        // Force the use case onto the download path — the pre-cached
        // local core would otherwise short-circuit before we get to
        // ask for the versioned binary.
        harness.coreRepo.forceMissingLocalCore = true
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        return harness
    }

    @Test
    fun launchSessionWithPinnedCoreUsesHistoricalBinary() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        // Seed a session with a pinned sha so auto-create skips and
        // ensureSession reuses ours, which has the pin set.
        harness.sessionRepo.sessions.add(
            com.spela.player.domain.model.GameSession(
                id = "s1",
                gameId = "1",
                name = "Pinned Run",
                pinnedCoreSha256 = "abc123pinnedsha",
            ),
        )

        setContent { harness.App() }
        advance(harness)

        // Launch via the game detail Play button. The ViewModel will
        // ensureSession → pick up s1 → fetch its pinnedCoreSha256 →
        // call downloadCoreByHash.
        onNodeWithTag("game_detail_play_button").performClick()
        advanceFully(harness)

        // The versioned endpoint was hit exactly once, with the right sha.
        assertEquals(1, harness.coreRepo.downloadByHashCalls.size)
        val (coreName, sha) = harness.coreRepo.downloadByHashCalls.first()
        assertEquals("nestopia", coreName)
        assertEquals("abc123pinnedsha", sha)
        // Fallback (unversioned) path MUST NOT be taken when the pin hits.
        assertEquals(0, harness.coreRepo.downloadCalls.size)
        // Emulation is running; no warning to surface.
        assertTrue(harness.libretroController.isRunning, "Emulation should start on pinned success")
        assertNull(
            harness.emulationViewModel.state.value.coreVersionWarning,
            "No fallback warning when the pinned binary was served",
        )
    }

    @Test
    fun launchSessionWithPrunedPinnedCoreFallsBackAndWarns() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        val prunedSha = "prunedsha999"
        // Mark this sha as pruned on the fake — downloadCoreByHash
        // will return CorePrunedException, triggering the fallback.
        harness.coreRepo.prunedHashes.add(prunedSha)
        harness.sessionRepo.sessions.add(
            com.spela.player.domain.model.GameSession(
                id = "s1",
                gameId = "1",
                name = "Ancient Run",
                pinnedCoreSha256 = prunedSha,
            ),
        )

        setContent { harness.App() }
        advance(harness)

        onNodeWithTag("game_detail_play_button").performClick()
        advanceFully(harness)

        // Hash path tried (and failed), then unversioned fallback taken.
        assertEquals(1, harness.coreRepo.downloadByHashCalls.size)
        assertEquals(1, harness.coreRepo.downloadCalls.size)
        // Emulation still proceeds — the pruned core is not fatal.
        assertTrue(harness.libretroController.isRunning, "Emulation should fall back to latest core")
        // The user-facing warning is populated with the exact copy
        // from the PO brief.
        val warning = harness.emulationViewModel.state.value.coreVersionWarning
        assertNotNull(warning, "coreVersionWarning should be set after fallback")
        assertEquals(
            "Original core version no longer available. The latest core may not load this save correctly.",
            warning,
        )
    }
}
