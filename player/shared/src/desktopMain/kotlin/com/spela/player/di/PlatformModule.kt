package com.spela.player.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.AuthEventBus
import com.spela.player.domain.controller.AchievementsController
import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.libretro.DesktopAchievementsController
import com.spela.player.libretro.DesktopLibretroController
import com.spela.player.libretro.DesktopScreenshotCapture
import com.spela.player.platform.secondarydisplay.DesktopSecondaryDisplay
import com.spela.player.libretro.LibretroJni
import com.spela.player.libretro.desktopDefaultRetroMapping
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.platform.DesktopFileStorage
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single {
        val dbPath = "spela.db"
        val dbFile = java.io.File(dbPath)

        // Validate existing database schema before opening
        if (dbFile.exists()) {
            try {
                val checkDriver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")
                val existingTables = checkDriver.executeQuery(
                    identifier = null,
                    sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                    mapper = { cursor ->
                        val tables = mutableSetOf<String>()
                        while (cursor.next().value) {
                            cursor.getString(0)?.let { tables.add(it) }
                        }
                        app.cash.sqldelight.db.QueryResult.Value(tables)
                    },
                    parameters = 0,
                ).value
                checkDriver.close()

                val expectedTables = setOf(
                    "ServerConnectionEntity", "DownloadEntity", "AuthTokenEntity",
                    "PlayHistoryEntity", "ShaderOverrideEntity", "KeyMappingEntity",
                    "GameKeyMappingEntity", "DeviceInfoEntity", "CachedPreferencesEntity",
                    "CachedConsoleEntity", "CachedGameEntity", "LocalSaveStateEntity",
                    "LocalSaveDataEntity",
                )
                val missingTables = expectedTables - existingTables
                if (missingTables.isNotEmpty()) {
                    println("Spela: Database schema incompatible, missing tables: $missingTables. Deleting database.")
                    dbFile.delete()
                    java.io.File("$dbPath-wal").delete()
                    java.io.File("$dbPath-shm").delete()
                }
            } catch (e: Exception) {
                println("Spela: Failed to validate database, deleting: ${e.message}")
                dbFile.delete()
                java.io.File("$dbPath-wal").delete()
                java.io.File("$dbPath-shm").delete()
            }
        }

        val driver = JdbcSqliteDriver("jdbc:sqlite:$dbPath")

        val currentVersion: Long = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor ->
                val hasRow = cursor.next()
                val version = if (hasRow.value) cursor.getLong(0) ?: 0L else 0L
                app.cash.sqldelight.db.QueryResult.Value(version)
            },
            parameters = 0,
        ).value

        val schemaVersion: Long = SpelaDatabase.Schema.version

        if (currentVersion == 0L) {
            // Check if tables already exist (DB created before versioning was added)
            val tableCount = driver.executeQuery(
                identifier = null,
                sql = "SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                mapper = { cursor ->
                    cursor.next()
                    app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0) ?: 0L)
                },
                parameters = 0,
            ).value

            if (tableCount == 0L) {
                SpelaDatabase.Schema.create(driver)
            }
            driver.execute(null, "PRAGMA user_version = $schemaVersion", 0)
        } else if (currentVersion < schemaVersion) {
            SpelaDatabase.Schema.migrate(driver, currentVersion, schemaVersion)
            driver.execute(null, "PRAGMA user_version = $schemaVersion", 0)
        }

        driver
    }
    single { SpelaDatabase(get<JdbcSqliteDriver>()) }
    single<HttpClientEngineFactory<*>> { CIO }
    single {
        val db = get<SpelaDatabase>()
        SpelaApiClient(
            engineFactory = CIO,
            tokenManager = get(),
            authEventBus = get<AuthEventBus>(),
            onTokenRefreshed = { accessToken, refreshToken ->
                db.spelaDatabaseQueries.insertTokens(accessToken, refreshToken, "")
            },
        )
    }
    single<FileStorage> { DesktopFileStorage() }
    single { LibretroJni() }
    single<LibretroController> { DesktopLibretroController(get(), get()) }
    single<AchievementsController> { DesktopAchievementsController(get(), get(), get()) }
    single<ScreenshotCapture> { DesktopScreenshotCapture(get<LibretroController>() as DesktopLibretroController) }
    single<PlatformSecondaryDisplay> { DesktopSecondaryDisplay() }
    single {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 15_000
            }
        }
    }

    // Platform default key mapping: retroButtonId -> Compose Key.keyCode (as Int)
    // Uses the same key code format as DesktopEmulationSurface for consistency.
    single<Map<Int, Int>>(named("platformDefaultMapping")) {
        desktopDefaultRetroMapping
    }
}
