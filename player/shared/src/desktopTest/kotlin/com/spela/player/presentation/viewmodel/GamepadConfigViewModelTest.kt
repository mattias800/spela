package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.libretro.GamepadPortManager
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GamepadConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }

    private val fakeRepo = FakeKeyMappingRepo()
    private val gamepadPortManager = GamepadPortManager(fakeRepo)

    private fun createViewModel(scope: CoroutineScope): GamepadConfigViewModel {
        return GamepadConfigViewModel(
            gamepadPortManager = gamepadPortManager,
            dispatchers = dispatchers,
            scope = scope,
        )
    }

    @Test
    fun emptyStateWhenNoDevicesConnected() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = createViewModel(scope)
        advanceTimeBy(300)
        assertTrue(vm.state.value.portAssignments.isEmpty())
        scope.cancel()
    }

    @Test
    fun connectedDeviceAppearsInState() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = createViewModel(scope)
        gamepadPortManager.connectDevice(1, "Xbox Controller")
        advanceTimeBy(300)

        val assignments = vm.state.value.portAssignments
        assertEquals(1, assignments.size)
        assertEquals(0, assignments[0].port)
        assertEquals("Xbox Controller", assignments[0].deviceName)
        assertEquals(1, assignments[0].deviceId)
        scope.cancel()
    }

    @Test
    fun multipleDevicesAppearSortedByPort() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = createViewModel(scope)
        gamepadPortManager.connectDevice(10, "Controller A")
        gamepadPortManager.connectDevice(20, "Controller B")
        advanceTimeBy(300)

        val assignments = vm.state.value.portAssignments
        assertEquals(2, assignments.size)
        assertEquals(0, assignments[0].port)
        assertEquals(1, assignments[1].port)
        scope.cancel()
    }

    @Test
    fun activityIsDetectedWithinTimeout() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = createViewModel(scope)
        gamepadPortManager.connectDevice(1, "Xbox")
        gamepadPortManager.reportActivity(0)
        advanceTimeBy(300)

        assertTrue(vm.state.value.portAssignments[0].isActive)
        scope.cancel()
    }

    @Test
    fun swapPortsIntentSwapsDevices() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = createViewModel(scope)
        gamepadPortManager.connectDevice(1, "Xbox")
        gamepadPortManager.connectDevice(2, "PS5")
        advanceTimeBy(300)

        vm.onIntent(GamepadConfigIntent.SwapPorts(0, 1))
        advanceTimeBy(300)

        val assignments = vm.state.value.portAssignments
        assertEquals("PS5", assignments.find { it.port == 0 }?.deviceName)
        assertEquals("Xbox", assignments.find { it.port == 1 }?.deviceName)
        scope.cancel()
    }

    @Test
    fun selectPortForMappingSetsSelectedPort() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = createViewModel(scope)
        assertNull(vm.state.value.selectedPort)

        vm.onIntent(GamepadConfigIntent.SelectPortForMapping(2))
        assertEquals(2, vm.state.value.selectedPort)
        scope.cancel()
    }

    @Test
    fun deselectPortClearsSelectedPort() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = createViewModel(scope)
        vm.onIntent(GamepadConfigIntent.SelectPortForMapping(2))
        assertEquals(2, vm.state.value.selectedPort)

        vm.onIntent(GamepadConfigIntent.DeselectPort)
        assertNull(vm.state.value.selectedPort)
        scope.cancel()
    }

    @Test
    fun disconnectedDeviceDisappearsFromState() = runTest(testDispatcher) {
        val scope = CoroutineScope(testDispatcher + Job())
        val vm = createViewModel(scope)
        gamepadPortManager.connectDevice(1, "Xbox")
        advanceTimeBy(300)
        assertEquals(1, vm.state.value.portAssignments.size)

        gamepadPortManager.disconnectDevice(1)
        advanceTimeBy(300)
        assertTrue(vm.state.value.portAssignments.isEmpty())
        scope.cancel()
    }

    private class FakeKeyMappingRepo : KeyMappingRepository {
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
