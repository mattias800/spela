package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ButtonInfo
import com.spela.player.domain.model.DefaultGamepadMapping
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.repository.GamepadMappingRepository
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.viewmodel.emulation.StubPreferencesRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GamepadMappingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }

    private val mappingRepo = FakeGamepadMappingRepo()
    private val portManager = GamepadPortManager(
        keyMappingRepository = NoOpKeyMappingRepo(),
        gamepadMappingRepository = mappingRepo,
    )

    /** RetroPad A on the NES layout (id 8) — an arbitrary console output to bind. */
    private val outputA = ButtonInfo(retroButtonId = 8, label = "A")
    private val DEVICE = 1

    private fun createViewModel(scope: CoroutineScope) = GamepadMappingViewModel(
        gamepadMappingRepository = mappingRepo,
        gamepadPortManager = portManager,
        preferencesRepository = StubPreferencesRepository(),
        dispatchers = dispatchers,
        scope = scope,
    )

    private fun ready(scope: CoroutineScope): GamepadMappingViewModel {
        val vm = createViewModel(scope)
        vm.onIntent(GamepadMappingIntent.Load("nes", 0))
        return vm
    }

    @Test
    fun startBindingActivatesCaptureAndSetsBindingOutput() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()

        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()

        assertEquals(outputA, vm.state.value.bindingOutput)
        assertTrue(portManager.bindCaptureActive.value)
        scope.cancel()
    }

    @Test
    fun heldPositionSurfacesInStateWhilePressed() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()
        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()

        portManager.reportBindPosition(DEVICE, GamepadPosition.SOUTH, pressed = true)
        advanceTimeBy(500)
        runCurrent()

        assertEquals(GamepadPosition.SOUTH, vm.state.value.bindingHeldPosition)
        scope.cancel()
    }

    @Test
    fun holdingPositionForHoldMsCommitsBindingExclusively() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()
        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()

        portManager.reportBindPosition(DEVICE, GamepadPosition.SOUTH, pressed = true)
        runCurrent()
        // Hold past the 2s threshold (but under the 5s abort).
        advanceTimeBy(GamepadMappingViewModel.HOLD_MS + 100)
        advanceUntilIdle()

        assertEquals(
            GamepadPosition.SOUTH to outputA.retroButtonId,
            mappingRepo.lastExclusiveBind,
        )
        // Session ended: capture off, prompt cleared.
        assertNull(vm.state.value.bindingOutput)
        assertFalse(portManager.bindCaptureActive.value)
        scope.cancel()
    }

    @Test
    fun releasingBeforeHoldMsDoesNotCommitAndResetsHold() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()
        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()

        portManager.reportBindPosition(DEVICE, GamepadPosition.SOUTH, pressed = true)
        runCurrent()
        advanceTimeBy(1000) // not yet 2s
        portManager.reportBindPosition(DEVICE, GamepadPosition.SOUTH, pressed = false)
        runCurrent()
        advanceTimeBy(1500) // would have crossed 2s had we kept holding

        assertNull(mappingRepo.lastExclusiveBind)
        assertNull(vm.state.value.bindingHeldPosition)
        // Still in the session, awaiting another press.
        assertEquals(outputA, vm.state.value.bindingOutput)
        scope.cancel()
    }

    @Test
    fun idleForAbortMsEndsSessionWithoutCommitting() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()
        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()

        advanceTimeBy(GamepadMappingViewModel.ABORT_MS + 100)
        advanceUntilIdle()

        assertNull(mappingRepo.lastExclusiveBind)
        assertNull(vm.state.value.bindingOutput)
        assertFalse(portManager.bindCaptureActive.value)
        scope.cancel()
    }

    @Test
    fun cancelBindingEndsSessionImmediately() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()
        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()

        vm.onIntent(GamepadMappingIntent.CancelBinding)
        runCurrent()

        assertNull(vm.state.value.bindingOutput)
        assertFalse(portManager.bindCaptureActive.value)
        assertNull(mappingRepo.lastExclusiveBind)
        scope.cancel()
    }

    @Test
    fun swappingHeldPositionRestartsHoldAndCommitsTheLater() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()
        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()

        // Hold SOUTH for 1.5s, then swap to WEST and hold the full 2s.
        portManager.reportBindPosition(DEVICE, GamepadPosition.SOUTH, pressed = true)
        runCurrent()
        advanceTimeBy(1500)
        portManager.reportBindPosition(DEVICE, GamepadPosition.SOUTH, pressed = false)
        portManager.reportBindPosition(DEVICE, GamepadPosition.WEST, pressed = true)
        runCurrent()
        advanceTimeBy(GamepadMappingViewModel.HOLD_MS + 100)
        advanceUntilIdle()

        assertEquals(
            GamepadPosition.WEST to outputA.retroButtonId,
            mappingRepo.lastExclusiveBind,
        )
        scope.cancel()
    }

    @Test
    fun reportBindInputIntentFeedsTheBinderAndCommits() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()
        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()

        // Drive the press through the intent (the path the Android dialog uses),
        // not the port manager directly.
        vm.onIntent(GamepadMappingIntent.ReportBindInput(GamepadPosition.SOUTH, pressed = true))
        runCurrent()
        advanceTimeBy(GamepadMappingViewModel.HOLD_MS + 100)
        advanceUntilIdle()

        assertEquals(GamepadPosition.SOUTH to outputA.retroButtonId, mappingRepo.lastExclusiveBind)
        scope.cancel()
    }

    @Test
    fun abortIsIdleOnlySoALateHoldStillCommits() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()
        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()

        // Idle almost the full abort window, THEN start holding. The idle-abort
        // must pause while held, so the 2s hold still commits (it would not if the
        // abort were a fixed 5s from session start).
        advanceTimeBy(GamepadMappingViewModel.ABORT_MS - 500)
        portManager.reportBindPosition(DEVICE, GamepadPosition.SOUTH, pressed = true)
        runCurrent()
        advanceTimeBy(GamepadMappingViewModel.HOLD_MS + 100)
        advanceUntilIdle()

        assertEquals(GamepadPosition.SOUTH to outputA.retroButtonId, mappingRepo.lastExclusiveBind)
        scope.cancel()
    }

    @Test
    fun bindingTickAdvancesOnEachPhaseChange() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = ready(scope)
        advanceUntilIdle()

        vm.onIntent(GamepadMappingIntent.StartBinding(outputA))
        runCurrent()
        val atStart = vm.state.value.bindingTick

        portManager.reportBindPosition(DEVICE, GamepadPosition.SOUTH, pressed = true)
        runCurrent()
        val afterPress = vm.state.value.bindingTick

        portManager.reportBindPosition(DEVICE, GamepadPosition.SOUTH, pressed = false)
        runCurrent()
        val afterRelease = vm.state.value.bindingTick

        assertTrue(afterPress > atStart, "tick should bump on press")
        assertTrue(afterRelease > afterPress, "tick should bump on release")
        scope.cancel()
    }

    // ── Fakes ────────────────────────────────────────────────────────────────

    /** In-memory mapping repo recording the last 1:1 bind, with real exclusive logic. */
    private class FakeGamepadMappingRepo : GamepadMappingRepository {
        private val overrides = HashMap<Pair<String, Int>, MutableMap<GamepadPosition, Int>>()
        var lastExclusiveBind: Pair<GamepadPosition, Int>? = null
            private set

        override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<GamepadPosition, Int> =
            DefaultGamepadMapping.POSITION_TO_RETRO + (overrides[consoleId to port] ?: emptyMap())

        override suspend fun setBinding(consoleId: String, port: Int, position: GamepadPosition, retroButtonId: Int) {
            overrides.getOrPut(consoleId to port) { mutableMapOf() }[position] = retroButtonId
        }

        override suspend fun bindPositionExclusive(
            consoleId: String,
            port: Int,
            position: GamepadPosition,
            retroButtonId: Int,
        ) {
            lastExclusiveBind = position to retroButtonId
            if (retroButtonId != GamepadMappingRepository.UNMAPPED) {
                for ((p, id) in getEffectiveMapping(consoleId, port)) {
                    if (id == retroButtonId && p != position) {
                        setBinding(consoleId, port, p, GamepadMappingRepository.UNMAPPED)
                    }
                }
            }
            setBinding(consoleId, port, position, retroButtonId)
        }

        override suspend fun resetToDefault(consoleId: String, port: Int) {
            overrides.remove(consoleId to port)
        }

        override fun getDefaultMapping(): Map<GamepadPosition, Int> = DefaultGamepadMapping.POSITION_TO_RETRO
    }

    private class NoOpKeyMappingRepo : KeyMappingRepository {
        override suspend fun getMappingForConsole(consoleId: String, port: Int): KeyMappingProfile? = null
        override suspend fun setBinding(consoleId: String, port: Int, retroButtonId: Int, platformKeyCode: Int) {}
        override suspend fun resetToDefault(consoleId: String, port: Int) {}
        override suspend fun clearBinding(consoleId: String, port: Int, retroButtonId: Int) {}
        override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<Int, Int> = emptyMap()
        override fun getDefaultMapping(): Map<Int, Int> = emptyMap()
        override fun getAvailablePresets(): List<KeyMappingPreset> = emptyList()
        override suspend fun applyPreset(presetId: String) {}
        override suspend fun ensureDefaultsApplied() {}
        override suspend fun getEffectiveMappingForGame(gameId: String, consoleId: String, port: Int): Map<Int, Int> = emptyMap()
        override suspend fun setGameMapping(gameId: String, bindings: Map<Int, Int>) {}
        override suspend fun clearGameMapping(gameId: String) {}
        override suspend fun hasGameMapping(gameId: String): Boolean = false
    }
}
