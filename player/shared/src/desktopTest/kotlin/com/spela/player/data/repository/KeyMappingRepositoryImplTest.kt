package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.DEFAULT_CONSOLE_ID
import com.spela.player.presentation.viewmodel.LibretroAnalog
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyMappingRepositoryImplTest {

    private lateinit var database: SpelaDatabase

    // Simulated platform defaults: retroButtonId -> platformKeyCode
    private val platformDefaults = mapOf(
        LibretroButtons.UP to 100,
        LibretroButtons.DOWN to 101,
        LibretroButtons.LEFT to 102,
        LibretroButtons.RIGHT to 103,
        LibretroButtons.A to 200,
        LibretroButtons.B to 201,
    )

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
    }

    private fun createRepo(): KeyMappingRepositoryImpl {
        return KeyMappingRepositoryImpl(database, platformDefaults)
    }

    @Test
    fun getMappingForConsoleReturnsNullWhenEmpty() = runTest {
        val repo = createRepo()
        val result = repo.getMappingForConsole("nes")
        assertNull(result)
    }

    @Test
    fun setBindingPersistsToDatabase() = runTest {
        val repo = createRepo()
        repo.setBinding("nes", 0, LibretroButtons.A, 999)

        val mapping = repo.getMappingForConsole("nes")
        assertNotNull(mapping)
        assertEquals("nes", mapping.consoleId)
        assertEquals(0, mapping.port)
        assertEquals(999, mapping.bindings[LibretroButtons.A])
    }

    @Test
    fun setMultipleBindingsForSameConsole() = runTest {
        val repo = createRepo()
        repo.setBinding("snes", 0, LibretroButtons.A, 500)
        repo.setBinding("snes", 0, LibretroButtons.B, 501)
        repo.setBinding("snes", 0, LibretroButtons.X, 502)

        val mapping = repo.getMappingForConsole("snes")
        assertNotNull(mapping)
        assertEquals(3, mapping.bindings.size)
        assertEquals(500, mapping.bindings[LibretroButtons.A])
        assertEquals(501, mapping.bindings[LibretroButtons.B])
        assertEquals(502, mapping.bindings[LibretroButtons.X])
    }

    @Test
    fun differentConsolesHaveSeparateMappings() = runTest {
        val repo = createRepo()
        repo.setBinding("nes", 0, LibretroButtons.A, 100)
        repo.setBinding("snes", 0, LibretroButtons.A, 200)

        val nesMapping = repo.getMappingForConsole("nes")
        val snesMapping = repo.getMappingForConsole("snes")

        assertNotNull(nesMapping)
        assertNotNull(snesMapping)
        assertEquals(100, nesMapping.bindings[LibretroButtons.A])
        assertEquals(200, snesMapping.bindings[LibretroButtons.A])
    }

    @Test
    fun differentPortsHaveSeparateMappings() = runTest {
        val repo = createRepo()
        repo.setBinding("nes", 0, LibretroButtons.A, 100)
        repo.setBinding("nes", 1, LibretroButtons.A, 200)

        val port0 = repo.getMappingForConsole("nes", 0)
        val port1 = repo.getMappingForConsole("nes", 1)

        assertNotNull(port0)
        assertNotNull(port1)
        assertEquals(100, port0.bindings[LibretroButtons.A])
        assertEquals(200, port1.bindings[LibretroButtons.A])
    }

    @Test
    fun resetToDefaultDeletesMappings() = runTest {
        val repo = createRepo()
        repo.setBinding("nes", 0, LibretroButtons.A, 999)
        repo.setBinding("nes", 0, LibretroButtons.B, 998)

        repo.resetToDefault("nes", 0)

        val mapping = repo.getMappingForConsole("nes")
        assertNull(mapping)
    }

    @Test
    fun resetOnlyAffectsSpecifiedConsoleAndPort() = runTest {
        val repo = createRepo()
        repo.setBinding("nes", 0, LibretroButtons.A, 100)
        repo.setBinding("snes", 0, LibretroButtons.A, 200)

        repo.resetToDefault("nes", 0)

        assertNull(repo.getMappingForConsole("nes"))
        assertNotNull(repo.getMappingForConsole("snes"))
    }

    @Test
    fun getEffectiveMappingReturnsConsoleSpecific() = runTest {
        val repo = createRepo()
        repo.setBinding("nes", 0, LibretroButtons.A, 999)

        val effective = repo.getEffectiveMapping("nes")
        assertEquals(999, effective[LibretroButtons.A])
    }

    @Test
    fun getEffectiveMappingFallsBackToGlobalDefault() = runTest {
        val repo = createRepo()
        // Set a global default mapping
        repo.setBinding(DEFAULT_CONSOLE_ID, 0, LibretroButtons.A, 888)

        // No NES-specific mapping -> should fall back to global default
        val effective = repo.getEffectiveMapping("nes")
        assertEquals(888, effective[LibretroButtons.A])
    }

    @Test
    fun getEffectiveMappingFallsBackToHardcodedDefaults() = runTest {
        val repo = createRepo()

        // No custom mappings at all -> should return hardcoded defaults
        val effective = repo.getEffectiveMapping("nes")
        assertEquals(platformDefaults, effective)
    }

    @Test
    fun getEffectiveMappingPrefersConsoleOverGlobal() = runTest {
        val repo = createRepo()
        repo.setBinding(DEFAULT_CONSOLE_ID, 0, LibretroButtons.A, 888)
        repo.setBinding("nes", 0, LibretroButtons.A, 999)

        val effective = repo.getEffectiveMapping("nes")
        assertEquals(999, effective[LibretroButtons.A])
    }

    @Test
    fun getDefaultMappingReturnsPlatformDefaults() {
        val repo = createRepo()
        assertEquals(platformDefaults, repo.getDefaultMapping())
    }

    @Test
    fun setBindingReplacesExistingKeyCode() = runTest {
        val repo = createRepo()
        repo.setBinding("nes", 0, LibretroButtons.A, 100)
        repo.setBinding("nes", 0, LibretroButtons.A, 200)

        val mapping = repo.getMappingForConsole("nes")
        assertNotNull(mapping)
        // The latest binding for the same button should win
        assertEquals(200, mapping.bindings[LibretroButtons.A])
    }

    @Test
    fun mappingsPersistAcrossRepositoryInstances() = runTest {
        val repo1 = createRepo()
        repo1.setBinding("nes", 0, LibretroButtons.A, 999)

        val repo2 = createRepo()
        val mapping = repo2.getMappingForConsole("nes")
        assertNotNull(mapping)
        assertEquals(999, mapping.bindings[LibretroButtons.A])
    }

    @Test
    fun ensureDefaultsAppliedNoOpsWithExistingMappings() = runTest {
        val repo = createRepo()
        // Add a mapping first
        repo.setBinding("nes", 0, LibretroButtons.A, 999)

        // ensureDefaultsApplied should not overwrite
        repo.ensureDefaultsApplied()

        val mapping = repo.getMappingForConsole("nes")
        assertNotNull(mapping)
        assertEquals(999, mapping.bindings[LibretroButtons.A])
    }

    @Test
    fun ensureDefaultsAppliedAppliesPresetWhenEmpty() = runTest {
        val repo = createRepo()
        // DB is empty, ensureDefaultsApplied should apply a preset
        repo.ensureDefaultsApplied()

        // Should have some mappings now (applied by detectDevicePreset())
        val effective = repo.getEffectiveMapping(DEFAULT_CONSOLE_ID)
        assertTrue(effective.isNotEmpty())
    }

    @Test
    fun gameKeyMappingOverride() = runTest {
        val repo = createRepo()

        // No game override initially
        assertFalse(repo.hasGameMapping("game1"))

        // Set per-game override
        val gameBindings = mapOf(LibretroButtons.A to 777, LibretroButtons.B to 778)
        repo.setGameMapping("game1", gameBindings)
        assertTrue(repo.hasGameMapping("game1"))

        // getEffectiveMappingForGame should return the override
        val effective = repo.getEffectiveMappingForGame("game1", "nes")
        assertEquals(777, effective[LibretroButtons.A])
        assertEquals(778, effective[LibretroButtons.B])
    }

    @Test
    fun getEffectiveMappingForGameFallsBackWithNoOverride() = runTest {
        val repo = createRepo()
        repo.setBinding("nes", 0, LibretroButtons.A, 999)

        // No game override → should fall back to console mapping
        val effective = repo.getEffectiveMappingForGame("game1", "nes")
        assertEquals(999, effective[LibretroButtons.A])
    }

    @Test
    fun clearGameMappingRemovesOverride() = runTest {
        val repo = createRepo()
        repo.setGameMapping("game1", mapOf(LibretroButtons.A to 777))
        assertTrue(repo.hasGameMapping("game1"))

        repo.clearGameMapping("game1")
        assertFalse(repo.hasGameMapping("game1"))

        // Should fall back to hardcoded defaults
        val effective = repo.getEffectiveMappingForGame("game1", "nes")
        assertEquals(platformDefaults, effective)
    }

    @Test
    fun virtualAnalogIdStoresAndRetrievesCorrectly() = runTest {
        val repo = createRepo()
        repo.setBinding("psx", 0, LibretroAnalog.LEFT_STICK_UP, 500)
        repo.setBinding("psx", 0, LibretroAnalog.RIGHT_STICK_LEFT, 501)

        val mapping = repo.getMappingForConsole("psx")
        assertNotNull(mapping)
        assertEquals(500, mapping.bindings[LibretroAnalog.LEFT_STICK_UP])
        assertEquals(501, mapping.bindings[LibretroAnalog.RIGHT_STICK_LEFT])
    }

    @Test
    fun virtualAnalogIdCoexistsWithRegularButtons() = runTest {
        val repo = createRepo()
        repo.setBinding("psx", 0, LibretroButtons.A, 200)
        repo.setBinding("psx", 0, LibretroAnalog.LEFT_STICK_UP, 500)

        val mapping = repo.getMappingForConsole("psx")
        assertNotNull(mapping)
        assertEquals(200, mapping.bindings[LibretroButtons.A])
        assertEquals(500, mapping.bindings[LibretroAnalog.LEFT_STICK_UP])
    }

    @Test
    fun applyPresetWritesToGlobalDefault() = runTest {
        val repo = createRepo()
        val presets = repo.getAvailablePresets()
        assertTrue(presets.isNotEmpty())

        repo.applyPreset(presets.first().id)

        // Global default should now contain the preset bindings
        val mapping = repo.getMappingForConsole(DEFAULT_CONSOLE_ID)
        assertNotNull(mapping)
        assertEquals(presets.first().bindings, mapping.bindings)
    }
}
