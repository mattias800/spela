package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.DefaultGamepadMapping
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GamepadMappingRepositoryImplTest {

    private lateinit var database: SpelaDatabase
    private lateinit var repo: GamepadMappingRepositoryImpl

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
        repo = GamepadMappingRepositoryImpl(database)
    }

    @Test
    fun emptyMappingResolvesToDefault() = runTest {
        assertEquals(
            DefaultGamepadMapping.POSITION_TO_RETRO,
            repo.getEffectiveMapping("nes", 0),
        )
    }

    @Test
    fun setBindingOverridesOnlyThatPosition() = runTest {
        repo.setBinding("nes", 0, GamepadPosition.SOUTH, LibretroButtons.A)
        val m = repo.getEffectiveMapping("nes", 0)
        assertEquals(LibretroButtons.A, m[GamepadPosition.SOUTH])
        // Everything else stays at default.
        assertEquals(LibretroButtons.A, m[GamepadPosition.EAST])
        assertEquals(LibretroButtons.Y, m[GamepadPosition.WEST])
        assertEquals(LibretroButtons.UP, m[GamepadPosition.DPAD_UP])
    }

    @Test
    fun guidingExampleNesAToSouthBToWest() = runTest {
        // NES A → south, NES B → west (RetroPad A=8, B=0).
        repo.setBinding("nes", 0, GamepadPosition.SOUTH, LibretroButtons.A)
        repo.setBinding("nes", 0, GamepadPosition.WEST, LibretroButtons.B)
        val m = repo.getEffectiveMapping("nes", 0)
        assertEquals(LibretroButtons.A, m[GamepadPosition.SOUTH])
        assertEquals(LibretroButtons.B, m[GamepadPosition.WEST])
    }

    @Test
    fun setBindingIsIdempotent() = runTest {
        repo.setBinding("nes", 0, GamepadPosition.SOUTH, LibretroButtons.A)
        repo.setBinding("nes", 0, GamepadPosition.SOUTH, LibretroButtons.A)
        repo.setBinding("nes", 0, GamepadPosition.SOUTH, LibretroButtons.X)
        // One row survives; the latest value wins.
        assertEquals(1, database.spelaDatabaseQueries.getAllGamepadMappings().executeAsList().size)
        assertEquals(LibretroButtons.X, repo.getEffectiveMapping("nes", 0)[GamepadPosition.SOUTH])
    }

    @Test
    fun mappingsAreScopedPerConsoleAndPort() = runTest {
        repo.setBinding("nes", 0, GamepadPosition.SOUTH, LibretroButtons.A)
        // A different console and a different port keep defaults.
        assertEquals(LibretroButtons.B, repo.getEffectiveMapping("snes", 0)[GamepadPosition.SOUTH])
        assertEquals(LibretroButtons.B, repo.getEffectiveMapping("nes", 1)[GamepadPosition.SOUTH])
    }

    @Test
    fun resetToDefaultClearsOverrides() = runTest {
        repo.setBinding("nes", 0, GamepadPosition.SOUTH, LibretroButtons.A)
        repo.resetToDefault("nes", 0)
        assertEquals(
            DefaultGamepadMapping.POSITION_TO_RETRO,
            repo.getEffectiveMapping("nes", 0),
        )
    }
}
