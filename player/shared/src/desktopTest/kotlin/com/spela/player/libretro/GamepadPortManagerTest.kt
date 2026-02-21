package com.spela.player.libretro

import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.repository.KeyMappingRepository
import kotlinx.coroutines.test.runTest
import com.spela.player.presentation.viewmodel.LibretroAnalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GamepadPortManagerTest {

    private val fakeRepo = FakeKeyMappingRepo()
    private val manager = GamepadPortManager(fakeRepo)

    @Test
    fun connectFirstDeviceAssignsPort0() {
        val port = manager.connectDevice(100, "Xbox Controller")
        assertEquals(0, port)
    }

    @Test
    fun connectSecondDeviceAssignsPort1() {
        manager.connectDevice(100, "Xbox")
        val port = manager.connectDevice(200, "PS5")
        assertEquals(1, port)
    }

    @Test
    fun connectSameDeviceTwiceReturnsSamePort() {
        val port1 = manager.connectDevice(100, "Xbox")
        val port2 = manager.connectDevice(100, "Xbox")
        assertEquals(port1, port2)
    }

    @Test
    fun disconnectFreePortForReuse() {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "PS5")
        manager.disconnectDevice(100)

        // New device should get port 0 (freed)
        val port = manager.connectDevice(300, "Switch Pro")
        assertEquals(0, port)
    }

    @Test
    fun getPortReturnsNegativeForUnknownDevice() {
        assertEquals(-1, manager.getPort(999))
    }

    @Test
    fun getPortReturnsAssignedPort() {
        manager.connectDevice(100, "Xbox")
        assertEquals(0, manager.getPort(100))
    }

    @Test
    fun maxPortsEnforced() {
        for (i in 0 until GamepadPortManager.MAX_PORTS) {
            val port = manager.connectDevice(i, "Device $i")
            assertEquals(i, port)
        }
        // Port 8 should be rejected
        val overflow = manager.connectDevice(999, "Overflow")
        assertEquals(-1, overflow)
    }

    @Test
    fun clearResetsAllState() {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "PS5")
        manager.clear()
        assertEquals(0, manager.connectedDeviceCount())
        assertEquals(-1, manager.getPort(100))
        assertEquals(-1, manager.getPort(200))
    }

    @Test
    fun connectedDeviceCountTracksDevices() {
        assertEquals(0, manager.connectedDeviceCount())
        manager.connectDevice(100)
        assertEquals(1, manager.connectedDeviceCount())
        manager.connectDevice(200)
        assertEquals(2, manager.connectedDeviceCount())
        manager.disconnectDevice(100)
        assertEquals(1, manager.connectedDeviceCount())
    }

    @Test
    fun assignmentsFlowUpdatesOnConnectDisconnect() {
        assertTrue(manager.assignments.value.isEmpty())
        manager.connectDevice(100, "Xbox")
        assertEquals(1, manager.assignments.value.size)
        assertEquals(100, manager.assignments.value[0].deviceId)
        assertEquals(0, manager.assignments.value[0].port)
        assertEquals("Xbox", manager.assignments.value[0].deviceName)

        manager.connectDevice(200, "PS5")
        assertEquals(2, manager.assignments.value.size)

        manager.disconnectDevice(100)
        assertEquals(1, manager.assignments.value.size)
        assertEquals(200, manager.assignments.value[0].deviceId)
    }

    @Test
    fun loadMappingForPortMakesKeyLookupWork() = runTest {
        manager.connectDevice(100, "Xbox")
        fakeRepo.effectiveMappings["snes:0"] = mapOf(0 to 96, 8 to 97) // B->96, A->97

        manager.loadMappingForPort(0, "snes")

        // keyCode -> retroButtonId (reversed from effective mapping)
        assertEquals(0, manager.mapKeyToLibretro(0, 96)) // 96 -> B(0)
        assertEquals(8, manager.mapKeyToLibretro(0, 97)) // 97 -> A(8)
        assertNull(manager.mapKeyToLibretro(0, 999))      // unmapped key
    }

    @Test
    fun loadAllMappingsLoadsForAllPorts() = runTest {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "PS5")
        fakeRepo.effectiveMappings["snes:0"] = mapOf(0 to 96)
        fakeRepo.effectiveMappings["snes:1"] = mapOf(8 to 97)

        manager.loadAllMappings("snes")

        assertEquals(0, manager.mapKeyToLibretro(0, 96))
        assertEquals(8, manager.mapKeyToLibretro(1, 97))
    }

    @Test
    fun mapKeyToLibretroReturnsNullForInvalidPort() {
        assertNull(manager.mapKeyToLibretro(-1, 96))
        assertNull(manager.mapKeyToLibretro(8, 96))
    }

    @Test
    fun disconnectClearsMappingForPort() = runTest {
        manager.connectDevice(100, "Xbox")
        fakeRepo.effectiveMappings["snes:0"] = mapOf(0 to 96)
        manager.loadMappingForPort(0, "snes")
        assertEquals(0, manager.mapKeyToLibretro(0, 96))

        manager.disconnectDevice(100)
        assertNull(manager.mapKeyToLibretro(0, 96))
    }

    @Test
    fun analogIdsFlowThroughMapKeyToLibretro() = runTest {
        manager.connectDevice(100, "Xbox")
        fakeRepo.effectiveMappings["psx:0"] = mapOf(
            0 to 96,                           // B -> 96
            LibretroAnalog.LEFT_STICK_UP to 500, // L-Stick Up -> 500
        )
        manager.loadMappingForPort(0, "psx")

        // Regular button
        assertEquals(0, manager.mapKeyToLibretro(0, 96))
        // Analog virtual button
        assertEquals(LibretroAnalog.LEFT_STICK_UP, manager.mapKeyToLibretro(0, 500))
        // Unmapped
        assertNull(manager.mapKeyToLibretro(0, 999))
    }

    @Test
    fun reportActivityUpdatesFlowForPort() {
        manager.connectDevice(100, "Xbox")
        manager.reportActivity(0)
        val activity = manager.portActivity.value
        assertTrue(activity.containsKey(0), "Activity map should have port 0")
        assertTrue(activity[0]!! > 0, "Timestamp should be positive")
    }

    @Test
    fun reportActivityIgnoresInvalidPort() {
        manager.reportActivity(-1)
        manager.reportActivity(8)
        assertTrue(manager.portActivity.value.isEmpty(), "Activity map should be empty for invalid ports")
    }

    @Test
    fun reportActivityIgnoresUnoccupiedPort() {
        manager.reportActivity(0)
        assertTrue(manager.portActivity.value.isEmpty(), "Activity map should be empty for unoccupied port")
    }

    @Test
    fun disconnectDeviceClearsActivity() {
        manager.connectDevice(100, "Xbox")
        manager.reportActivity(0)
        assertTrue(manager.portActivity.value.containsKey(0))

        manager.disconnectDevice(100)
        assertTrue(!manager.portActivity.value.containsKey(0), "Activity should be cleared after disconnect")
    }

    @Test
    fun clearResetsActivity() {
        manager.connectDevice(100, "Xbox")
        manager.reportActivity(0)
        assertTrue(manager.portActivity.value.isNotEmpty())

        manager.clear()
        assertTrue(manager.portActivity.value.isEmpty(), "Activity should be cleared after clear()")
    }

    @Test
    fun portReassignmentKeepsIndependentMappings() = runTest {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "PS5")
        fakeRepo.effectiveMappings["snes:0"] = mapOf(0 to 96)
        fakeRepo.effectiveMappings["snes:1"] = mapOf(8 to 97)
        manager.loadAllMappings("snes")

        // Disconnect device on port 0, reconnect a new device
        manager.disconnectDevice(100)
        manager.connectDevice(300, "Switch Pro")
        fakeRepo.effectiveMappings["snes:0"] = mapOf(3 to 13) // START -> Enter
        manager.loadMappingForPort(0, "snes")

        // Port 0 should have new mapping
        assertEquals(3, manager.mapKeyToLibretro(0, 13))
        assertNull(manager.mapKeyToLibretro(0, 96)) // old mapping gone

        // Port 1 should be unchanged
        assertEquals(8, manager.mapKeyToLibretro(1, 97))
    }

    @Test
    fun swapPortsSwapsDevicesAndMappings() = runTest {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "PS5")
        fakeRepo.effectiveMappings["snes:0"] = mapOf(0 to 96)
        fakeRepo.effectiveMappings["snes:1"] = mapOf(8 to 97)
        manager.loadAllMappings("snes")

        // Before swap
        assertEquals(0, manager.getPort(100))
        assertEquals(1, manager.getPort(200))
        assertEquals(0, manager.mapKeyToLibretro(0, 96))
        assertEquals(8, manager.mapKeyToLibretro(1, 97))

        manager.swapPorts(0, 1)

        // After swap: devices moved
        assertEquals(1, manager.getPort(100))
        assertEquals(0, manager.getPort(200))
        // Mappings moved with ports
        assertEquals(8, manager.mapKeyToLibretro(0, 97))
        assertEquals(0, manager.mapKeyToLibretro(1, 96))
    }

    @Test
    fun swapPortsSamePortIsNoOp() {
        manager.connectDevice(100, "Xbox")
        manager.swapPorts(0, 0)
        assertEquals(0, manager.getPort(100))
    }

    @Test
    fun swapPortsOutOfRangeIsNoOp() {
        manager.connectDevice(100, "Xbox")
        manager.swapPorts(0, 8)
        assertEquals(0, manager.getPort(100))
        manager.swapPorts(-1, 0)
        assertEquals(0, manager.getPort(100))
    }

    @Test
    fun swapPortsWithEmptyPortSwapsOccupancy() {
        manager.connectDevice(100, "Xbox")
        assertEquals(0, manager.getPort(100))

        manager.swapPorts(0, 1)

        // Device should now be on port 1
        assertEquals(1, manager.getPort(100))
        // Port 0 should be free for a new device
        val newPort = manager.connectDevice(200, "PS5")
        assertEquals(0, newPort)
    }

    @Test
    fun swapPortsUpdatesAssignmentsFlow() {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "PS5")

        val beforeSwap = manager.assignments.value
        assertEquals(0, beforeSwap.find { it.deviceId == 100 }?.port)
        assertEquals(1, beforeSwap.find { it.deviceId == 200 }?.port)

        manager.swapPorts(0, 1)

        val afterSwap = manager.assignments.value
        assertEquals(1, afterSwap.find { it.deviceId == 100 }?.port)
        assertEquals(0, afterSwap.find { it.deviceId == 200 }?.port)
    }

    private class FakeKeyMappingRepo : KeyMappingRepository {
        val effectiveMappings = mutableMapOf<String, Map<Int, Int>>()

        override suspend fun getMappingForConsole(consoleId: String, port: Int): KeyMappingProfile? = null
        override suspend fun setBinding(consoleId: String, port: Int, retroButtonId: Int, platformKeyCode: Int) {}
        override suspend fun resetToDefault(consoleId: String, port: Int) {}
        override suspend fun clearBinding(consoleId: String, port: Int, retroButtonId: Int) {}
        override suspend fun getEffectiveMapping(consoleId: String, port: Int): Map<Int, Int> {
            return effectiveMappings["$consoleId:$port"] ?: emptyMap()
        }
        override fun getDefaultMapping(): Map<Int, Int> = emptyMap()
        override fun getAvailablePresets(): List<com.spela.player.domain.model.KeyMappingPreset> = emptyList()
        override suspend fun applyPreset(presetId: String) {}
        override suspend fun ensureDefaultsApplied() {}
        override suspend fun getEffectiveMappingForGame(gameId: String, consoleId: String, port: Int): Map<Int, Int> {
            return getEffectiveMapping(consoleId, port)
        }
        override suspend fun setGameMapping(gameId: String, bindings: Map<Int, Int>) {}
        override suspend fun clearGameMapping(gameId: String) {}
        override suspend fun hasGameMapping(gameId: String): Boolean = false
    }
}
