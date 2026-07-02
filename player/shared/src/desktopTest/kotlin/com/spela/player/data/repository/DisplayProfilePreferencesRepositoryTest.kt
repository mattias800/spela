package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.DisplayAspectChoice
import com.spela.player.domain.model.RenderScale
import com.spela.player.domain.model.RenderScaleChoice
import com.spela.player.domain.model.UserPreferences
import com.spela.player.presentation.viewmodel.emulation.StubMockEngineFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayProfilePreferencesRepositoryTest {
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
    fun displayAspectAutoClearsWhileOriginalPersists() {
        val db = database()
        val repo = repo(db)

        assertEquals(DisplayAspectChoice.AUTO, repo.resolveDisplayAspectChoice("game1", "wii"))

        repo.setDisplayAspectChoice("game1", "wii", DisplayAspectChoice.ORIGINAL)
        assertEquals(DisplayAspectChoice.ORIGINAL, repo.resolveDisplayAspectChoice("game1", "wii"))

        repo.setDisplayAspectChoice("game1", "wii", DisplayAspectChoice.AUTO)
        assertEquals(DisplayAspectChoice.AUTO, repo.resolveDisplayAspectChoice("game1", "wii"))
    }

    @Test
    fun legacyWidescreenStorageValuesMapToDisplayAspectChoices() {
        val db = database()
        val repo = repo(db)

        db.spelaDatabaseQueries.insertDeviceSetting("game_widescreen_mode:game1", "stretch")
        assertEquals(DisplayAspectChoice.SIXTEEN_NINE, repo.resolveDisplayAspectChoice("game1", "wii"))

        db.spelaDatabaseQueries.insertDeviceSetting("game_widescreen_mode:game1", "4_3")
        assertEquals(DisplayAspectChoice.ORIGINAL, repo.resolveDisplayAspectChoice("game1", "wii"))
    }

    @Test
    fun explicitNativeRenderScaleOverridesServerPreferenceUntilAutoClearsIt() {
        val db = database()
        val repo = repo(db)
        val serverPreferences = UserPreferences(
            consoleRenderScales = mapOf("gc" to RenderScale.FOUR_X),
        )

        assertEquals(
            RenderScaleChoice.FOUR_X,
            repo.resolveRenderScaleChoice("gc", serverPreferences),
        )

        repo.setRenderScaleChoice("gc", RenderScaleChoice.NATIVE)
        assertEquals(
            RenderScaleChoice.NATIVE,
            repo.resolveRenderScaleChoice("gc", serverPreferences),
        )

        repo.setRenderScaleChoice("gc", RenderScaleChoice.AUTO)
        assertEquals(
            RenderScaleChoice.FOUR_X,
            repo.resolveRenderScaleChoice("gc", serverPreferences),
        )
    }
}
