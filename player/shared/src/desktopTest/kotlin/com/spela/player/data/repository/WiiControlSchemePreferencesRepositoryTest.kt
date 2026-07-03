package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.WiiControlScheme
import com.spela.player.presentation.viewmodel.emulation.StubMockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WiiControlSchemePreferencesRepositoryTest {
    private fun database(): SpelaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        return SpelaDatabase(driver)
    }

    private fun repo(db: SpelaDatabase): PreferencesRepositoryImpl {
        val apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager())
            .also { it.setBaseUrl("http://localhost:8080") }
        return PreferencesRepositoryImpl(
            apiClient = apiClient,
            database = db,
            deviceManager = DeviceManager(db, apiClient),
            keyMappingRepository = KeyMappingRepositoryImpl(db, emptyMap()),
        )
    }

    @Test
    fun defaultsToNunchukWhenNothingStored() {
        val repo = repo(database())
        assertEquals(WiiControlScheme.NUNCHUK, repo.resolveWiiControlScheme("game1"))
    }

    @Test
    fun overrideRoundTripsAndNunchukClears() {
        val db = database()
        val repo = repo(db)

        repo.setWiiControlScheme("game1", WiiControlScheme.CLASSIC_CONTROLLER)
        assertEquals(WiiControlScheme.CLASSIC_CONTROLLER, repo.resolveWiiControlScheme("game1"))

        // Selecting the default clears the stored row (like AUTO widescreen).
        repo.setWiiControlScheme("game1", WiiControlScheme.NUNCHUK)
        assertEquals(WiiControlScheme.NUNCHUK, repo.resolveWiiControlScheme("game1"))
        assertNull(
            db.spelaDatabaseQueries
                .getDeviceSetting("game_wii_control_scheme:game1")
                .executeAsOneOrNull(),
        )
    }

    @Test
    fun schemeIsPerGame() {
        val repo = repo(database())
        repo.setWiiControlScheme("game1", WiiControlScheme.WIIMOTE_SIDEWAYS)
        assertEquals(WiiControlScheme.WIIMOTE_SIDEWAYS, repo.resolveWiiControlScheme("game1"))
        assertEquals(WiiControlScheme.NUNCHUK, repo.resolveWiiControlScheme("game2"))
    }

    @Test
    fun unknownStoredValueFallsBackToDefault() {
        val db = database()
        val repo = repo(db)
        db.spelaDatabaseQueries.insertDeviceSetting("game_wii_control_scheme:game1", "bogus")
        assertEquals(WiiControlScheme.NUNCHUK, repo.resolveWiiControlScheme("game1"))
    }
}
