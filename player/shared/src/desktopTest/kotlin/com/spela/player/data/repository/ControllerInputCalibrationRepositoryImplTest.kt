package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.GamepadPosition
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControllerInputCalibrationRepositoryImplTest {

    private lateinit var database: SpelaDatabase
    private lateinit var repo: ControllerInputCalibrationRepositoryImpl

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
        repo = ControllerInputCalibrationRepositoryImpl(database)
    }

    @Test
    fun absentCalibrationIsEmpty() {
        assertTrue(repo.get("pad-a").isEmpty())
    }

    @Test
    fun setThenGetRoundTrips() {
        repo.put(
            "pad-a",
            mapOf(
                GamepadPosition.EAST to GamepadPosition.SOUTH,
                GamepadPosition.SOUTH to GamepadPosition.EAST,
            ),
        )

        assertEquals(
            mapOf(
                GamepadPosition.SOUTH to GamepadPosition.EAST,
                GamepadPosition.EAST to GamepadPosition.SOUTH,
            ),
            repo.get("pad-a"),
        )
    }

    @Test
    fun identityMappingsAreNotStored() {
        repo.put("pad-a", mapOf(GamepadPosition.SOUTH to GamepadPosition.SOUTH))

        assertTrue(repo.get("pad-a").isEmpty())
    }

    @Test
    fun clearRemovesCalibration() {
        repo.put("pad-a", mapOf(GamepadPosition.EAST to GamepadPosition.SOUTH))
        repo.clear("pad-a")

        assertTrue(repo.get("pad-a").isEmpty())
    }
}
