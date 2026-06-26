package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.DEFAULT_CONSOLE_ID
import com.spela.player.domain.model.DefaultKeyMappings
import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.presentation.intent.KeyMappingIntent
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class KeyMappingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeRepo: FakeKeyMappingRepository
    private lateinit var fakePrefsRepo: FakePreferencesRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeKeyMappingRepository()
        fakePrefsRepo = FakePreferencesRepository()
        fakePrefsRepo.keyMappingRepo = fakeRepo
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): KeyMappingViewModel {
        val scope = CoroutineScope(testDispatcher)
        return KeyMappingViewModel(
            keyMappingRepository = fakeRepo,
            preferencesRepository = fakePrefsRepo,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun initialStateIsLoading() {
        val vm = createViewModel()
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun loadMappingSetsBindingsAndButtons() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("nes", state.consoleId)
        // NES has 8 buttons (d-pad + A, B, Start, Select)
        assertEquals(DefaultKeyMappings.NES.buttons.size, state.buttonsForConsole.size)
        // Should have the default mapping
        assertEquals(fakeRepo.getDefaultMapping(), state.currentBindings)
    }

    @Test
    fun saveAsGameOverridePushesToServer() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadGameMapping("game-1", "nes"))
        advanceUntilIdle()
        vm.onIntent(KeyMappingIntent.StartSingleButtonMap(8))
        advanceUntilIdle()
        vm.onIntent(KeyMappingIntent.CaptureButton(99))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.SaveAsGameOverride("game-1"))
        advanceUntilIdle()

        // Local + server both updated (#1336).
        assertTrue(vm.state.value.hasGameOverride)
        assertEquals("game-1", fakePrefsRepo.pushedGameMapping?.first)
        assertEquals(99, fakePrefsRepo.pushedGameMapping?.second?.get(8))
    }

    @Test
    fun clearGameOverrideDeletesOnServer() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadGameMapping("game-1", "nes"))
        advanceUntilIdle()
        vm.onIntent(KeyMappingIntent.SaveAsGameOverride("game-1"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.ClearGameOverride("game-1"))
        advanceUntilIdle()

        assertFalse(vm.state.value.hasGameOverride)
        assertEquals("game-1", fakePrefsRepo.deletedGameMappingId)
    }

    @Test
    fun loadGameMappingPullsServerOverride() = runTest(testDispatcher) {
        // Another device's override lives only on the server.
        fakePrefsRepo.serverGameMapping = mapOf(8 to 77)
        val vm = createViewModel()

        vm.onIntent(KeyMappingIntent.LoadGameMapping("game-1", "nes"))
        advanceUntilIdle()

        // The pull ran and imported the server override into the effective mapping.
        assertTrue(fakePrefsRepo.syncedGameMappingIds.contains("game-1"))
        assertTrue(vm.state.value.hasGameOverride)
        assertEquals(77, vm.state.value.currentBindings[8])
    }

    @Test
    fun startWizardSetsFirstButton() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.StartWizard("nes"))

        val state = vm.state.value
        assertTrue(state.isWizardMode)
        assertEquals(1, state.mappingStep)
        assertEquals(DefaultKeyMappings.NES.buttons.size, state.totalSteps)
        assertNotNull(state.currentMappingButton)
        assertEquals(DefaultKeyMappings.NES.buttons.first().retroButtonId, state.currentMappingButton)
    }

    @Test
    fun captureButtonInWizardAdvancesToNextStep() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.StartWizard("nes"))
        val firstButton = vm.state.value.currentMappingButton!!

        vm.onIntent(KeyMappingIntent.CaptureButton(999))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.mappingStep)
        assertEquals(999, state.currentBindings[firstButton])
        // Current button should have advanced
        assertEquals(DefaultKeyMappings.NES.buttons[1].retroButtonId, state.currentMappingButton)
    }

    @Test
    fun skipButtonAdvancesWithoutBinding() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        val defaultBindings = vm.state.value.currentBindings.toMap()
        vm.onIntent(KeyMappingIntent.StartWizard("nes"))
        vm.onIntent(KeyMappingIntent.SkipButton)

        val state = vm.state.value
        assertEquals(2, state.mappingStep)
        // Bindings should not have changed
        assertEquals(defaultBindings, state.currentBindings)
    }

    @Test
    fun startSingleButtonMapEntersListeningMode() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.StartSingleButtonMap(LibretroButtons.A))

        val state = vm.state.value
        assertFalse(state.isWizardMode)
        assertEquals(LibretroButtons.A, state.currentMappingButton)
    }

    @Test
    fun captureButtonInSingleModeExitsListening() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.StartSingleButtonMap(LibretroButtons.A))
        vm.onIntent(KeyMappingIntent.CaptureButton(777))
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.currentMappingButton)
        assertEquals(777, state.currentBindings[LibretroButtons.A])
    }

    @Test
    fun resetAllReloadsDefaults() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        // Set a custom binding
        fakeRepo.setBinding("nes", 0, LibretroButtons.A, 999)
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()
        assertEquals(999, vm.state.value.currentBindings[LibretroButtons.A])

        // Reset
        vm.onIntent(KeyMappingIntent.ResetAll)
        advanceUntilIdle()

        // Should be back to platform defaults
        assertEquals(fakeRepo.getDefaultMapping(), vm.state.value.currentBindings)
    }

    @Test
    fun cancelMappingInSingleModeExitsListening() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.StartSingleButtonMap(LibretroButtons.A))
        vm.onIntent(KeyMappingIntent.CancelMapping)

        assertNull(vm.state.value.currentMappingButton)
    }

    @Test
    fun cancelWizardReloadsFromStorage() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.StartWizard("nes"))
        vm.onIntent(KeyMappingIntent.CaptureButton(999))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.CancelMapping)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isWizardMode)
        assertNull(state.currentMappingButton)
    }

    @Test
    fun finishMappingExitsAllModes() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.StartWizard("nes"))
        vm.onIntent(KeyMappingIntent.FinishMapping)

        val state = vm.state.value
        assertFalse(state.isWizardMode)
        assertNull(state.currentMappingButton)
        assertEquals(0, state.mappingStep)
    }

    @Test
    fun captureButtonTriggersServerPush() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.StartSingleButtonMap(LibretroButtons.A))
        vm.onIntent(KeyMappingIntent.CaptureButton(777))
        advanceUntilIdle()

        assertTrue(fakePrefsRepo.pushKeyMappingsCalled)
    }

    @Test
    fun resetAllTriggersServerPush() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.ResetAll)
        advanceUntilIdle()

        assertTrue(fakePrefsRepo.pushKeyMappingsCalled)
    }

    @Test
    fun clearCurrentBindingRemovesBindingAndExitsListening() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        // Set a custom binding first
        vm.onIntent(KeyMappingIntent.StartSingleButtonMap(LibretroButtons.A))
        vm.onIntent(KeyMappingIntent.CaptureButton(777))
        advanceUntilIdle()
        assertEquals(777, vm.state.value.currentBindings[LibretroButtons.A])

        // Now enter listening mode again and clear it
        vm.onIntent(KeyMappingIntent.StartSingleButtonMap(LibretroButtons.A))
        vm.onIntent(KeyMappingIntent.ClearCurrentBinding)
        advanceUntilIdle()

        assertNull(vm.state.value.currentMappingButton)
        assertFalse(vm.state.value.currentBindings.containsKey(LibretroButtons.A))
    }

    @Test
    fun clearCurrentBindingInWizardAdvancesToNext() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.StartWizard("nes"))
        val firstButton = vm.state.value.currentMappingButton!!
        assertEquals(1, vm.state.value.mappingStep)

        vm.onIntent(KeyMappingIntent.ClearCurrentBinding)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.mappingStep)
        // The first button should have been cleared (removed from bindings)
        assertFalse(state.currentBindings.containsKey(firstButton))
        // Should have advanced to the second button
        assertEquals(DefaultKeyMappings.NES.buttons[1].retroButtonId, state.currentMappingButton)
    }

    @Test
    fun clearCurrentBindingWhenNotListeningIsNoOp() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        val bindingsBefore = vm.state.value.currentBindings.toMap()
        vm.onIntent(KeyMappingIntent.ClearCurrentBinding)
        advanceUntilIdle()

        assertEquals(bindingsBefore, vm.state.value.currentBindings)
        assertNull(vm.state.value.currentMappingButton)
    }

    @Test
    fun loadMappingWithUnknownConsoleUsesFullLayout() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(KeyMappingIntent.LoadMapping("unknown_console"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(DefaultKeyMappings.FULL.buttons.size, state.buttonsForConsole.size)
    }

    @Test
    fun applyPresetReloadsGamepadPortManager() = runTest(testDispatcher) {
        val portManager = GamepadPortManager(fakeRepo)
        val scope = CoroutineScope(testDispatcher)
        val vm = KeyMappingViewModel(
            keyMappingRepository = fakeRepo,
            preferencesRepository = fakePrefsRepo,
            gamepadPortManager = portManager,
            dispatchers = testDispatchers,
            scope = scope,
        )

        // Load initial mapping for a console
        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        // Register a device so port 0 has an active mapping
        portManager.connectDevice(1, "Test Controller")

        // Apply a preset with different bindings
        fakeRepo.presetBindings = mapOf(
            LibretroButtons.A to 300,
            LibretroButtons.B to 301,
        )
        vm.onIntent(KeyMappingIntent.ShowPresetPicker)
        advanceUntilIdle()

        vm.onIntent(KeyMappingIntent.ApplyPreset("test-preset"))
        advanceUntilIdle()

        // The GamepadPortManager should now have the new bindings
        // (mapKeyToLibretro uses the reverse mapping: keyCode -> retroButton)
        assertEquals(LibretroButtons.A, portManager.mapKeyToLibretro(0, 300))
        assertEquals(LibretroButtons.B, portManager.mapKeyToLibretro(0, 301))
    }

    @Test
    fun resetAllReloadsGamepadPortManager() = runTest(testDispatcher) {
        val portManager = GamepadPortManager(fakeRepo)
        val scope = CoroutineScope(testDispatcher)
        val vm = KeyMappingViewModel(
            keyMappingRepository = fakeRepo,
            preferencesRepository = fakePrefsRepo,
            gamepadPortManager = portManager,
            dispatchers = testDispatchers,
            scope = scope,
        )

        vm.onIntent(KeyMappingIntent.LoadMapping("nes"))
        advanceUntilIdle()

        // Register a device
        portManager.connectDevice(1, "Test Controller")

        // Modify a binding then reset
        vm.onIntent(KeyMappingIntent.ResetAll)
        advanceUntilIdle()

        // After reset, default mappings should be loaded in GamepadPortManager
        val defaultA = fakeRepo.getDefaultMapping()[LibretroButtons.A]!!
        assertEquals(LibretroButtons.A, portManager.mapKeyToLibretro(0, defaultA))
    }

    // Fake repositories for testing

    private class FakePreferencesRepository : PreferencesRepository {
        var pushKeyMappingsCalled = false
        var pushedGameMapping: Pair<String, Map<Int, Int>>? = null
        var deletedGameMappingId: String? = null
        val syncedGameMappingIds = mutableListOf<String>()
        var serverGameMapping: Map<Int, Int> = emptyMap()
        var keyMappingRepo: KeyMappingRepository? = null
        override suspend fun getPreferences(): Result<UserPreferences> = Result.success(UserPreferences())
        override suspend fun updatePreferences(
            showPerformanceOverlay: Boolean?, autoSaveEnabled: Boolean?, autoLoadSaveEnabled: Boolean?,
            autoUpdateCoresEnabled: Boolean?,
            selectedShader: String?, selectedTheme: String?, consoleShaders: Map<String, String>?,
            consoleSaveStatePolicies: Map<String, String>?,
            gameSaveStatePolicies: Map<String, String>?,
            defaultSecondScreenPage: String?,
        ): Result<UserPreferences> = Result.success(UserPreferences())
        override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
        override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
        override fun getAllDeviceShaderOverrides(): Map<String, ShaderPreset> = emptyMap()
        override suspend fun syncDeviceShaderOverrides() {}
        override suspend fun resolveShader(consoleId: String): ShaderPreset = ShaderPreset.NONE
        override suspend fun pushDeviceShaderOverridesToServer() {}
        override suspend fun syncKeyMappingsFromServer() {}
        override suspend fun pushKeyMappingsToServer() { pushKeyMappingsCalled = true }
        override suspend fun pushGameKeyMappingToServer(gameId: String, bindings: Map<Int, Int>) {
            pushedGameMapping = gameId to bindings
        }
        override suspend fun deleteGameKeyMappingOnServer(gameId: String) { deletedGameMappingId = gameId }
        override suspend fun syncGameKeyMappingFromServer(gameId: String) {
            syncedGameMappingIds.add(gameId)
            if (serverGameMapping.isNotEmpty()) keyMappingRepo?.setGameMapping(gameId, serverGameMapping)
        }
        override fun getOrientationLock(): String = "auto"
        override fun setOrientationLock(mode: String) {}
        override fun getControlTab(consoleId: String): String =
            if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
        override fun setControlTab(consoleId: String, tab: String) {}
        override fun getConsoleListGrouping(): String = "generation"
        override fun setConsoleListGrouping(grouping: String) {}
        override fun getConfirmButtonConvention(): String = "xbox"
        override fun setConfirmButtonConvention(convention: String) {}
    }

    private class FakeKeyMappingRepository : KeyMappingRepository {
        private val storage = mutableMapOf<String, MutableMap<Int, Int>>() // "consoleId:port" -> bindings
        private val gameMappings = mutableMapOf<String, MutableMap<Int, Int>>()
        var presetBindings: Map<Int, Int>? = null

        private val defaults = mapOf(
            LibretroButtons.UP to 100,
            LibretroButtons.DOWN to 101,
            LibretroButtons.A to 200,
            LibretroButtons.B to 201,
        )

        private fun key(consoleId: String, port: Int) = "$consoleId:$port"

        override suspend fun getMappingForConsole(consoleId: String, port: Int): KeyMappingProfile? {
            val bindings = storage[key(consoleId, port)] ?: return null
            return KeyMappingProfile(consoleId, port, bindings.toMap())
        }

        override suspend fun setBinding(consoleId: String, port: Int, retroButtonId: Int, platformKeyCode: Int) {
            val k = key(consoleId, port)
            storage.getOrPut(k) { mutableMapOf() }[retroButtonId] = platformKeyCode
        }

        override suspend fun resetToDefault(consoleId: String, port: Int) {
            storage.remove(key(consoleId, port))
        }

        override suspend fun clearBinding(consoleId: String, port: Int, retroButtonId: Int) {
            storage[key(consoleId, port)]?.remove(retroButtonId)
        }

        override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<Int, Int> {
            storage[key(consoleId, port)]?.let { return it.toMap() }
            if (consoleId != DEFAULT_CONSOLE_ID) {
                storage[key(DEFAULT_CONSOLE_ID, port)]?.let { return it.toMap() }
            }
            return defaults
        }

        override fun getDefaultMapping(): Map<Int, Int> = defaults
        override fun getAvailablePresets(): List<KeyMappingPreset> = listOf(
            KeyMappingPreset("test-preset", "Test Preset", "Test preset description", presetBindings ?: defaults),
        )
        override suspend fun applyPreset(presetId: String) {
            val bindings = presetBindings ?: return
            storage[key(DEFAULT_CONSOLE_ID, 0)] = bindings.toMutableMap()
        }
        override suspend fun ensureDefaultsApplied() {}
        override suspend fun getEffectiveMappingForGame(gameId: String, consoleId: String, port: Int): Map<Int, Int> {
            return gameMappings[gameId] ?: getEffectiveMapping(consoleId, port)
        }
        override suspend fun setGameMapping(gameId: String, bindings: Map<Int, Int>) {
            gameMappings[gameId] = bindings.toMutableMap()
        }
        override suspend fun clearGameMapping(gameId: String) { gameMappings.remove(gameId) }
        override suspend fun hasGameMapping(gameId: String): Boolean = gameMappings.containsKey(gameId)
    }
}
