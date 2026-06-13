package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.ControllerStyle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ControllerStyleOverrideRepositoryImplTest {

    private lateinit var database: SpelaDatabase
    private lateinit var repo: ControllerStyleOverrideRepositoryImpl

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
        repo = ControllerStyleOverrideRepositoryImpl(database)
    }

    @Test
    fun absentOverrideIsAuto() = runTest {
        assertNull(repo.getOverride("Xbox Wireless Controller"))
    }

    @Test
    fun setThenGetRoundTrips() = runTest {
        repo.setOverride("Wireless Controller", ControllerStyle.Nintendo)
        assertEquals(ControllerStyle.Nintendo, repo.getOverride("Wireless Controller"))
    }

    @Test
    fun explicitGenericIsStoredNotAuto() = runTest {
        repo.setOverride("Some USB Gamepad", ControllerStyle.Generic)
        assertEquals(ControllerStyle.Generic, repo.getOverride("Some USB Gamepad"))
    }

    @Test
    fun setNullClearsOverride() = runTest {
        repo.setOverride("Pad", ControllerStyle.Xbox)
        repo.setOverride("Pad", null)
        assertNull(repo.getOverride("Pad"))
    }

    @Test
    fun overridesAreKeyedPerController() = runTest {
        repo.setOverride("Pad A", ControllerStyle.Xbox)
        repo.setOverride("Pad B", ControllerStyle.PlayStation)
        assertEquals(ControllerStyle.Xbox, repo.getOverride("Pad A"))
        assertEquals(ControllerStyle.PlayStation, repo.getOverride("Pad B"))
    }
}
