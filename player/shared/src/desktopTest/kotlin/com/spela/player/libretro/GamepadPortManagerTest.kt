package com.spela.player.libretro

import com.spela.player.domain.model.ControllerStyle
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.domain.model.KeyMappingProfile
import com.spela.player.domain.repository.KeyMappingRepository
import kotlinx.coroutines.test.runTest
import com.spela.player.presentation.viewmodel.LibretroAnalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun connectDeviceRecordsStyleOnAssignment() {
        manager.connectDevice(deviceId = 1, deviceName = "Xbox Wireless Controller", style = ControllerStyle.Xbox)
        val a = manager.assignments.value.single()
        assertEquals(ControllerStyle.Xbox, a.style)
        assertEquals("Xbox Wireless Controller", a.deviceName)
    }

    @Test
    fun styleDefaultsToGeneric() {
        manager.connectDevice(deviceId = 2, deviceName = "")
        assertEquals(ControllerStyle.Generic, manager.assignments.value.single().style)
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

    @Test
    fun controllerStatusEmptyByDefault() {
        val status = manager.controllerStatus.value
        assertEquals(0, status.connectedCount)
        assertFalse(status.isMultiplayer)
        status.ports.forEach { assertFalse(it.connected) }
    }

    @Test
    fun controllerStatusUpdatesOnConnect() {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "DualSense")
        val status = manager.controllerStatus.value
        assertEquals(2, status.connectedCount)
        assertTrue(status.isMultiplayer)
        assertTrue(status.ports[0].connected)
        assertTrue(status.ports[1].connected)
        assertFalse(status.ports[2].connected)
    }

    @Test
    fun controllerStatusUpdatesOnDisconnect() {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "DualSense")
        assertTrue(manager.controllerStatus.value.isMultiplayer)

        manager.disconnectDevice(100)
        val status = manager.controllerStatus.value
        assertEquals(1, status.connectedCount)
        assertFalse(status.isMultiplayer)
        assertFalse(status.ports[0].connected)
        assertTrue(status.ports[1].connected)
    }

    @Test
    fun controllerStatusReflectsActivityAfterReport() {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "DualSense")
        manager.reportActivity(0)
        val status = manager.controllerStatus.value
        assertTrue(status.ports[0].connected)
        assertTrue(status.ports[1].connected)
    }

    @Test
    fun controllerStatusClearsOnClear() {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "DualSense")
        assertTrue(manager.controllerStatus.value.isMultiplayer)

        manager.clear()
        val status = manager.controllerStatus.value
        assertEquals(0, status.connectedCount)
        assertFalse(status.isMultiplayer)
    }

    @Test
    fun controllerStatusUpdatesOnSwap() {
        manager.connectDevice(100, "Xbox")
        manager.connectDevice(200, "DualSense")
        val before = manager.controllerStatus.value
        assertTrue(before.ports[0].connected)
        assertTrue(before.ports[1].connected)

        manager.swapPorts(0, 1)
        val after = manager.controllerStatus.value
        // Both ports still connected after swap
        assertTrue(after.ports[0].connected)
        assertTrue(after.ports[1].connected)
    }

    // ── Live input tester: per-device pressedPositions (#1355/#1359) ──────────

    @Test
    fun reportPositionInputTracksPressAndReleaseForDeviceUnderTest() {
        manager.setTestCaptureDevice(10)
        manager.reportPositionInput(10, GamepadPosition.SOUTH, pressed = true)
        assertTrue(GamepadPosition.SOUTH in manager.pressedPositions.value)
        manager.reportPositionInput(10, GamepadPosition.SOUTH, pressed = false)
        assertFalse(GamepadPosition.SOUTH in manager.pressedPositions.value)
    }

    @Test
    fun reportPressedPositionsReplacesSetForDeviceUnderTest() {
        manager.setTestCaptureDevice(10)
        manager.reportPressedPositions(10, setOf(GamepadPosition.SOUTH, GamepadPosition.EAST))
        assertEquals(setOf(GamepadPosition.SOUTH, GamepadPosition.EAST), manager.pressedPositions.value)
        manager.reportPressedPositions(10, setOf(GamepadPosition.WEST))
        assertEquals(setOf(GamepadPosition.WEST), manager.pressedPositions.value)
    }

    @Test
    fun pressedPositionsReflectsOnlyDeviceUnderTest() {
        manager.setTestCaptureDevice(10)
        manager.reportPositionInput(10, GamepadPosition.SOUTH, pressed = true)
        // Input from a different controller must not leak into the tester.
        manager.reportPositionInput(20, GamepadPosition.NORTH, pressed = true)
        assertEquals(setOf(GamepadPosition.SOUTH), manager.pressedPositions.value)
    }

    @Test
    fun noPressedPositionsWhenNoDeviceUnderTest() {
        manager.reportPositionInput(10, GamepadPosition.SOUTH, pressed = true)
        assertTrue(manager.pressedPositions.value.isEmpty())
    }

    @Test
    fun switchingTestDeviceClearsPreviousHighlights() {
        manager.setTestCaptureDevice(10)
        manager.reportPositionInput(10, GamepadPosition.SOUTH, pressed = true)
        assertEquals(setOf(GamepadPosition.SOUTH), manager.pressedPositions.value)
        manager.setTestCaptureDevice(20)
        assertTrue(manager.pressedPositions.value.isEmpty())
    }

    @Test
    fun deactivatingTestCaptureClearsHighlights() {
        manager.setTestCaptureDevice(10)
        manager.reportPositionInput(10, GamepadPosition.SOUTH, pressed = true)
        assertTrue(GamepadPosition.SOUTH in manager.pressedPositions.value)
        manager.setTestCaptureDevice(null)
        assertTrue(manager.pressedPositions.value.isEmpty())
    }

    @Test
    fun disconnectClearsPressedPositionsForDeviceUnderTest() {
        manager.connectDevice(deviceId = 7, deviceName = "Pad")
        manager.setTestCaptureDevice(7)
        manager.reportPositionInput(7, GamepadPosition.SOUTH, pressed = true)
        assertTrue(GamepadPosition.SOUTH in manager.pressedPositions.value)
        manager.disconnectDevice(7)
        assertFalse(GamepadPosition.SOUTH in manager.pressedPositions.value)
    }

    // ── Hold-to-bind capture: any-device + D-pad (#1377) ─────────────────────

    @Test
    fun bindCaptureTracksPressAndReleaseIncludingDpad() {
        manager.setBindCaptureActive(true)
        manager.reportBindPosition(10, GamepadPosition.DPAD_UP, pressed = true)
        assertTrue(GamepadPosition.DPAD_UP in manager.bindPressedPositions.value)
        manager.reportBindPosition(10, GamepadPosition.DPAD_UP, pressed = false)
        assertFalse(GamepadPosition.DPAD_UP in manager.bindPressedPositions.value)
    }

    @Test
    fun bindCaptureMergesAcrossDevices() {
        manager.setBindCaptureActive(true)
        manager.reportBindPosition(10, GamepadPosition.SOUTH, pressed = true)
        // Unlike the tester, binding captures any controller — both presses show.
        manager.reportBindPosition(20, GamepadPosition.NORTH, pressed = true)
        assertEquals(
            setOf(GamepadPosition.SOUTH, GamepadPosition.NORTH),
            manager.bindPressedPositions.value,
        )
    }

    @Test
    fun bindCaptureIgnoresPressesWhenInactive() {
        manager.reportBindPosition(10, GamepadPosition.SOUTH, pressed = true)
        assertTrue(manager.bindPressedPositions.value.isEmpty())
    }

    @Test
    fun deactivatingBindCaptureClearsHeldPositions() {
        manager.setBindCaptureActive(true)
        manager.reportBindPosition(10, GamepadPosition.SOUTH, pressed = true)
        assertTrue(GamepadPosition.SOUTH in manager.bindPressedPositions.value)
        manager.setBindCaptureActive(false)
        assertTrue(manager.bindPressedPositions.value.isEmpty())
    }

    @Test
    fun bindCaptureIsIndependentFromInputTester() {
        // The tester (per-device, no D-pad) and the binder (any-device, incl D-pad)
        // use separate signals and must not bleed into each other.
        manager.setTestCaptureDevice(10)
        manager.setBindCaptureActive(true)
        manager.reportPositionInput(10, GamepadPosition.SOUTH, pressed = true)
        manager.reportBindPosition(20, GamepadPosition.DPAD_LEFT, pressed = true)
        assertEquals(setOf(GamepadPosition.SOUTH), manager.pressedPositions.value)
        assertEquals(setOf(GamepadPosition.DPAD_LEFT), manager.bindPressedPositions.value)
    }

    @Test
    fun disconnectClearsBindHeldPositions() {
        manager.connectDevice(deviceId = 7, deviceName = "Pad")
        manager.setBindCaptureActive(true)
        manager.reportBindPosition(7, GamepadPosition.SOUTH, pressed = true)
        assertTrue(GamepadPosition.SOUTH in manager.bindPressedPositions.value)
        manager.disconnectDevice(7)
        assertFalse(GamepadPosition.SOUTH in manager.bindPressedPositions.value)
    }

    // ── Right-stick viewport scroll signal (#1362) ───────────────────────────

    @Test
    fun rightStickScrollDefaultsToZeroAndReportsValue() {
        assertEquals(0f, manager.rightStickScroll.value)
        manager.setRightStickScroll(0.7f)
        assertEquals(0.7f, manager.rightStickScroll.value)
        manager.setRightStickScroll(0f)
        assertEquals(0f, manager.rightStickScroll.value)
    }

    // ── Player-slot assignment (#1359) ───────────────────────────────────────

    @Test
    fun newControllerAutoClaimsLowestFreeSlot() {
        assertEquals(0, manager.connectDevice(100, "Xbox"))
        assertEquals(1, manager.connectDevice(200, "PS5"))
        assertEquals(listOf(0, 1), manager.connectedControllers.value.map { it.slot })
    }

    @Test
    fun deviceOnSlotReportsOccupant() {
        manager.connectDevice(100, "Xbox")
        assertEquals(100, manager.deviceOnSlot(0))
        assertNull(manager.deviceOnSlot(1))
    }

    @Test
    fun assignSlotMovesControllerFromItsOldSlot() {
        manager.connectDevice(100, "Xbox") // slot 0
        manager.assignSlot(100, 3)
        assertEquals(3, manager.getPort(100))
        assertNull(manager.deviceOnSlot(0))
    }

    @Test
    fun assignSlotToOccupiedSlotMovesAndClearsOldController() {
        manager.connectDevice(100, "Xbox") // -> P1 (slot 0)
        manager.connectDevice(200, "PS5") // -> P2 (slot 1)
        manager.assignSlot(200, 0) // move PS5 to P1, clearing Xbox
        assertEquals(0, manager.getPort(200))
        assertEquals(-1, manager.getPort(100))
        // Xbox stays connected, just unassigned.
        assertTrue(manager.connectedControllers.value.any { it.deviceId == 100 && it.slot == null })
    }

    @Test
    fun clearAssignmentUnassignsButKeepsConnected() {
        manager.connectDevice(100, "Xbox") // -> P1
        manager.clearAssignment(100)
        assertEquals(-1, manager.getPort(100))
        assertTrue(manager.connectedControllers.value.any { it.deviceId == 100 && it.slot == null })
        // The freed slot 0 is available for a newly connected controller.
        assertEquals(0, manager.connectDevice(200, "PS5"))
    }

    // ── Persistence across reconnect (#1359) ─────────────────────────────────

    @Test
    fun persistedSlotIsRestoredOnReconnect() {
        val m = GamepadPortManager(fakeRepo, controllerAssignmentRepository = FakeControllerAssignmentRepo())
        m.connectDevice(100, "Xbox", stableKey = "key-xbox") // auto-claims 0, remembers
        m.assignSlot(100, 2) // remembers key-xbox -> 2
        m.disconnectDevice(100)
        // Reconnect with a new ephemeral id but the same stable key.
        assertEquals(2, m.connectDevice(101, "Xbox", stableKey = "key-xbox"))
    }

    @Test
    fun clearedStateIsRememberedAcrossReconnect() {
        val m = GamepadPortManager(fakeRepo, controllerAssignmentRepository = FakeControllerAssignmentRepo())
        m.connectDevice(100, "Xbox", stableKey = "key-xbox") // -> 0
        m.clearAssignment(100) // remembers key-xbox -> cleared
        m.disconnectDevice(100)
        assertEquals(-1, m.connectDevice(101, "Xbox", stableKey = "key-xbox"))
    }

    @Test
    fun rememberedSlotTakenByAnotherLeavesReconnectUnassigned() {
        val m = GamepadPortManager(fakeRepo, controllerAssignmentRepository = FakeControllerAssignmentRepo())
        m.connectDevice(100, "Xbox", stableKey = "key-xbox") // remembers 0
        m.disconnectDevice(100)
        m.connectDevice(200, "PS5", stableKey = "key-ps5") // claims freed slot 0
        // Xbox reconnects, but its remembered slot 0 is taken -> unassigned.
        assertEquals(-1, m.connectDevice(101, "Xbox", stableKey = "key-xbox"))
    }

    private class FakeControllerAssignmentRepo :
        com.spela.player.domain.repository.ControllerAssignmentRepository {
        private val store = mutableMapOf<String, Int?>()
        override fun getAll(): Map<String, Int?> = store.toMap()
        override fun put(stableKey: String, slot: Int?) {
            store[stableKey] = slot
        }
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
