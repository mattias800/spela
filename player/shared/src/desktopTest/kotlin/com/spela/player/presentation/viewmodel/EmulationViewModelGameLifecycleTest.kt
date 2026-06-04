package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.presentation.viewmodel.emulation.StubGameRepository
import com.spela.player.domain.model.SaveStateChoice
import com.spela.player.domain.model.SaveStatePolicyTier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences
import com.spela.player.presentation.state.SlotPickerMode
import com.spela.player.presentation.intent.EmulationIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelGameLifecycleTest {

    private lateinit var builder: EmulationViewModelTestBuilder

    @BeforeTest
    fun setup() {
        builder = EmulationViewModelTestBuilder()
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        builder.tearDown()
        Dispatchers.resetMain()
    }

    @Test
    fun startGameSetsIsLoadingTrue() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        // Before any dispatching, isLoading should be set synchronously
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun startGameSetsIsRunningTrueAfterLoad() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun startGameSetsGameIdInState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("my-game-42"))
        builder.advanceTimeBy(100)
        assertEquals("my-game-42", vm.state.value.gameId)
    }

    @Test
    fun startGameFetchesGameDetail() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals("Test Game", vm.state.value.gameTitle)
        assertEquals("nes", vm.state.value.consoleId)
    }

    @Test
    fun startGameLoadsUserPreferences() = runTest {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(showPerformanceOverlay = true)
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.showPerformanceOverlay)
    }

    @Test
    fun startGameSetsSaveStatesOptedOutWhenPreferenceDisabled() = runTest {
        // Console "nes" → matches the StubGameRepository default detail.
        // The user has explicitly opted out of save states for that
        // console; the in-game overlay must hide the Save / Load /
        // Challenge buttons (#804 phase 4 spec point d).
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(
                consoleSaveStatePolicies = mapOf("nes" to SaveStateChoice.Disabled),
            ),
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.saveStatesOptedOut)
    }

    @Test
    fun startGameKeepsSaveStatesEnabledWhenNoOverride() = runTest {
        // Default preferences have an empty policy map. The flag must
        // stay false so the overlay continues to show Save / Load.
        builder.preferencesRepository.preferencesResult = Result.success(UserPreferences())
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.saveStatesOptedOut)
    }

    @Test
    fun startGameFiresFirstLaunchPromptForLargeTierWithoutOverride() = runTest {
        // Large-tier console + no override → AskOnce. The dialog
        // must appear so the user makes a deliberate choice on the
        // first GC/Wii/PS2 launch.
        builder.gameRepository = StubGameRepository(
            consoleId = "gc",
            consoleName = "Nintendo GameCube",
            consoleSaveStatePolicy = com.spela.player.domain.model.SaveStatePolicyTier.Large,
        )
        builder.preferencesRepository.preferencesResult = Result.success(UserPreferences())
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.showSaveStatePrompt)
        assertEquals("gc", vm.state.value.saveStatePromptConsoleAbbr)
        assertEquals("Nintendo GameCube", vm.state.value.saveStatePromptConsoleName)
        assertFalse(vm.state.value.saveStatesOptedOut)
    }

    @Test
    fun startGameSkipsFirstLaunchPromptWhenLargeTierAlreadyResolved() = runTest {
        builder.gameRepository = StubGameRepository(
            consoleId = "gc",
            consoleSaveStatePolicy = com.spela.player.domain.model.SaveStatePolicyTier.Large,
        )
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(
                consoleSaveStatePolicies = mapOf("gc" to SaveStateChoice.Enabled),
            ),
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.showSaveStatePrompt)
    }

    @Test
    fun startGameDoesNotFirePromptForSmallTier() = runTest {
        // Small/medium tiers default to Enabled, never AskOnce.
        builder.gameRepository = StubGameRepository(
            consoleId = "nes",
            consoleSaveStatePolicy = com.spela.player.domain.model.SaveStatePolicyTier.Small,
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.showSaveStatePrompt)
    }

    @Test
    fun acceptSaveStatesForConsoleClearsPromptAndWritesEnabled() = runTest {
        builder.gameRepository = StubGameRepository(
            consoleId = "gc",
            consoleSaveStatePolicy = com.spela.player.domain.model.SaveStatePolicyTier.Large,
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.showSaveStatePrompt)

        vm.onIntent(EmulationIntent.AcceptSaveStatesForConsole)
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.showSaveStatePrompt)
        assertFalse(vm.state.value.saveStatesOptedOut)
        assertEquals(
            mapOf("gc" to SaveStateChoice.Enabled.apiId),
            builder.preferencesRepository.lastConsoleSaveStatePoliciesUpdate,
        )
    }

    @Test
    fun rejectSaveStatesForConsoleClearsPromptAndWritesDisabled() = runTest {
        builder.gameRepository = StubGameRepository(
            consoleId = "gc",
            consoleSaveStatePolicy = com.spela.player.domain.model.SaveStatePolicyTier.Large,
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.RejectSaveStatesForConsole)
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.showSaveStatePrompt)
        // Reject = Disabled → overlay greying flag must flip too so
        // the user doesn't see the buttons re-enabled until next launch.
        assertTrue(vm.state.value.saveStatesOptedOut)
        assertEquals(
            mapOf("gc" to SaveStateChoice.Disabled.apiId),
            builder.preferencesRepository.lastConsoleSaveStatePoliciesUpdate,
        )
    }

    @Test
    fun deferSaveStateChoiceWritesEnabledSoPromptDoesNotReFire() = runTest {
        // The "Decide per game" button records `enabled` at the
        // console level; the per-game toggle (future slice) handles
        // game-by-game work. Without this, the prompt would re-fire
        // on every launch because the policy stayed at AskOnce.
        builder.gameRepository = StubGameRepository(
            consoleId = "gc",
            consoleSaveStatePolicy = com.spela.player.domain.model.SaveStatePolicyTier.Large,
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.DeferSaveStateChoiceToPerGame)
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.showSaveStatePrompt)
        assertFalse(vm.state.value.saveStatesOptedOut)
        assertEquals(
            mapOf("gc" to SaveStateChoice.Enabled.apiId),
            builder.preferencesRepository.lastConsoleSaveStatePoliciesUpdate,
        )
    }

    @Test
    fun startGameKeepsSaveStatesEnabledWhenOverrideForDifferentConsole() = runTest {
        // The user opted out of GameCube but is launching an NES game.
        // Per-console isolation — opt-out for one console must not
        // leak to another.
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(
                consoleSaveStatePolicies = mapOf("gc" to SaveStateChoice.Disabled),
            ),
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.saveStatesOptedOut)
    }

    // ── Slot-primary UX (#804 phase 5) ──────────────────────────────────

    @Test
    fun saveIntentOnLargeTierOpensSlotPickerInsteadOfSaving() = runTest {
        builder.gameRepository = StubGameRepository(
            consoleId = "gc",
            consoleSaveStatePolicy = SaveStatePolicyTier.Large,
        )
        // Pre-resolve the AskOnce prompt so it doesn't sit on top
        // and confuse the test.
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(consoleSaveStatePolicies = mapOf("gc" to SaveStateChoice.Enabled)),
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals(SaveStatePolicyTier.Large, vm.state.value.consoleSaveStatePolicyTier)
        assertNull(vm.state.value.slotPickerMode)

        vm.onIntent(EmulationIntent.SaveState)
        builder.advanceTimeBy(100)
        assertEquals(SlotPickerMode.Save, vm.state.value.slotPickerMode)
    }

    @Test
    fun loadIntentOnMediumTierOpensSlotPicker() = runTest {
        builder.gameRepository = StubGameRepository(
            consoleId = "psx",
            consoleSaveStatePolicy = SaveStatePolicyTier.Medium,
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.LoadState)
        builder.advanceTimeBy(100)
        assertEquals(SlotPickerMode.Load, vm.state.value.slotPickerMode)
    }

    @Test
    fun saveIntentOnSmallTierKeepsHistoricalNamedSaveBehaviour() = runTest {
        // Phase 5 must not regress the small-tier UX. Tapping Save on
        // an NES game still calls SaveManager.saveState() rather than
        // opening a picker — the picker is the right answer for ~30 MB
        // states, the wrong answer for ~30 KB ones.
        builder.gameRepository = StubGameRepository(
            consoleId = "nes",
            consoleSaveStatePolicy = SaveStatePolicyTier.Small,
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.SaveState)
        builder.advanceTimeBy(100)
        assertNull(vm.state.value.slotPickerMode)
    }

    @Test
    fun pickingSlotFromSavePickerCommitsAndDismisses() = runTest {
        builder.gameRepository = StubGameRepository(
            consoleId = "gc",
            consoleSaveStatePolicy = SaveStatePolicyTier.Large,
        )
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(consoleSaveStatePolicies = mapOf("gc" to SaveStateChoice.Enabled)),
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        vm.onIntent(EmulationIntent.SaveState)
        builder.advanceTimeBy(100)
        assertEquals(SlotPickerMode.Save, vm.state.value.slotPickerMode)

        vm.onIntent(EmulationIntent.SaveToSlot(3))
        builder.advanceTimeBy(100)
        // Picker closes immediately so the user sees the regular
        // "Saving…" feedback rather than a stuck modal.
        assertNull(vm.state.value.slotPickerMode)
        assertEquals(3, vm.state.value.activeSlot)
    }

    @Test
    fun dismissSlotPickerClosesWithoutSaving() = runTest {
        builder.gameRepository = StubGameRepository(
            consoleId = "gc",
            consoleSaveStatePolicy = SaveStatePolicyTier.Large,
        )
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(consoleSaveStatePolicies = mapOf("gc" to SaveStateChoice.Enabled)),
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        vm.onIntent(EmulationIntent.SaveState)
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.DismissSlotPicker)
        builder.advanceTimeBy(100)
        assertNull(vm.state.value.slotPickerMode)
    }

    // ────────────────────────────────────────────────────────────────────

    @Test
    fun startGameResolvesShader() = runTest {
        builder.preferencesRepository.resolveShaderResult = ShaderPreset.CRT_SIMPLE
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals(ShaderPreset.CRT_SIMPLE, vm.state.value.selectedShader)
    }

    @Test
    fun startGameCallsLoadCoreThenLoadGameThenStart() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals(1, builder.libretroController.loadCoreCallCount)
        assertEquals(1, builder.libretroController.loadGameCallCount)
        assertEquals(1, builder.libretroController.startCallCount)
    }

    @Test
    fun startGameDetectsDualScreenConsoleNds() = runTest {
        builder.gameRepository = StubGameRepository(consoleId = "nds")
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isDualScreenConsole)
        assertEquals(192, vm.state.value.dualScreenSplitY)
    }

    @Test
    fun startGameDetectsDualScreenConsole3ds() = runTest {
        builder.gameRepository = StubGameRepository(consoleId = "3ds")
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isDualScreenConsole)
        assertEquals(240, vm.state.value.dualScreenSplitY)
    }

    @Test
    fun startGameSetsSessionElapsedSecondsToZero() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals(0, vm.state.value.sessionElapsedSeconds)
    }

    @Test
    fun sessionTimerIncrementsEverySecond() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        // Advance past the 3s delay + 3 timer ticks (at t=4000, 5000, 6000)
        builder.advanceTimeBy(6100)
        assertTrue(vm.state.value.sessionElapsedSeconds >= 3)
    }

    @Test
    fun sessionTimerStopsOnStopGame() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        builder.advanceTimeBy(5000)
        val elapsed = vm.state.value.sessionElapsedSeconds
        assertTrue(elapsed >= 1)
        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(3000)
        // After stop, isRunning should be false
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun pauseGameCallsControllerAndSetsState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        vm.onIntent(EmulationIntent.PauseGame)
        assertTrue(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.pauseCallCount)
    }

    @Test
    fun resumeGameCallsControllerAndSetsState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        vm.onIntent(EmulationIntent.PauseGame)
        assertTrue(vm.state.value.isPaused)
        vm.onIntent(EmulationIntent.ResumeGame)
        assertFalse(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.resumeCallCount)
    }

    @Test
    fun stopGameResetsRunningState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isPaused)
    }

    @Test
    fun startGameFailureShowsError() = runTest {
        builder.libretroController.loadCoreShouldThrow = RuntimeException("Core load failed")
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Failed to start emulation"))
    }

    @Test
    fun startGameResetsSupportsSaveStatesToTrue() = runTest {
        // First game: core does NOT support save states
        builder.libretroController.supportsSaveStatesResult = false
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(4000) // Wait past the 3-second probe
        assertFalse(vm.state.value.supportsSaveStates, "First game should not support save states")

        // Stop the first game
        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(100)

        // Second game: core DOES support save states
        builder.libretroController.supportsSaveStatesResult = true
        vm.onIntent(EmulationIntent.StartGame("game1"))
        // Immediately after startGame, supportsSaveStates should be reset to true
        assertTrue(vm.state.value.supportsSaveStates, "supportsSaveStates should reset to true on new game start")
    }

    @Test
    fun stopGameClearsGameIdentityFields() = runTest {
        // #1298 regression: finishStopGame() must clear the game identity so a
        // later overlay/launch can't show the previous game's title or frame.
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals("Test Game", vm.state.value.gameTitle)
        assertEquals("nes", vm.state.value.consoleId)

        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(100)
        assertEquals("", vm.state.value.gameId)
        assertEquals("", vm.state.value.gameTitle)
        assertEquals("", vm.state.value.consoleId)
        assertEquals("", vm.state.value.consoleName)
    }

    @Test
    fun startingNewGameRecreatesStateFromScratch() = runTest {
        // #1298 regression: launching a second game must be state-wise
        // identical to launching the first — the prior game's title/console
        // must not linger (the bug: overlay showed "Final Fantasy X" while
        // Ocarina of Time was starting). The reset is synchronous, so the
        // stale title must be gone immediately, before the new detail loads.
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals("Test Game", vm.state.value.gameTitle)

        vm.onIntent(EmulationIntent.StartGame("game2"))
        // No advanceTimeBy: assert the synchronous from-scratch reset.
        assertEquals("game2", vm.state.value.gameId)
        assertEquals("", vm.state.value.gameTitle)
        assertEquals("", vm.state.value.consoleId)
        assertEquals("", vm.state.value.consoleName)
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun startGameAutoCreatesSessionWhenNoneProvided() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        // Session should have been auto-created
        assertNotNull(vm.state.value.sessionId, "Session should be auto-created when none provided")
        assertEquals(1, builder.sessionRepository.createSessionCallCount)
        assertEquals("Default", builder.sessionRepository.lastCreatedSessionName)
    }

    @Test
    fun startGameReusesExistingSession() = runTest {
        builder.sessionRepository.existingSessions = listOf(
            com.spela.player.domain.model.GameSession(id = "existing-42", gameId = "game1", name = "My Save")
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        // Should reuse existing session, not create a new one
        assertEquals("existing-42", vm.state.value.sessionId)
        assertEquals(0, builder.sessionRepository.createSessionCallCount)
    }

    @Test
    fun startGameWithExplicitSessionIdDoesNotAutoCreate() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", sessionId = "explicit-session"))
        builder.advanceTimeBy(100)

        assertEquals("explicit-session", vm.state.value.sessionId)
        assertEquals(0, builder.sessionRepository.createSessionCallCount)
    }

    @Test
    fun newGameCreatesNewSessionInsteadOfReusing() = runTest {
        // Set up an existing session
        builder.sessionRepository.existingSessions = listOf(
            com.spela.player.domain.model.GameSession(id = "existing-42", gameId = "game1", name = "Default")
        )
        val vm = builder.build()

        // Start game with skipAutoLoad=true + forceNewSession=true ("New Game")
        vm.onIntent(EmulationIntent.StartGame("game1", skipAutoLoad = true, forceNewSession = true))
        builder.advanceTimeBy(100)

        // Should create a NEW session, not reuse the existing one
        assertEquals(1, builder.sessionRepository.createSessionCallCount)
        assertNotNull(vm.state.value.sessionId)
        // The new session ID should NOT be the existing one
        assertTrue(vm.state.value.sessionId != "existing-42",
            "New Game should create a fresh session, not reuse existing")
    }
}
