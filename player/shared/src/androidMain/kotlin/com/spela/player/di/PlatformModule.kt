package com.spela.player.di

import android.view.KeyEvent
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.spela.player.data.local.DatabaseHealthCheck
import com.spela.player.data.local.DatabaseResetHelper
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.controller.AchievementsController
import com.spela.player.libretro.AndroidAchievementsController
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.libretro.LibretroJni
import com.spela.player.platform.secondarydisplay.AndroidSecondaryDisplay
import com.spela.player.platform.secondarydisplay.SecondaryDisplayManager
import com.spela.player.presentation.secondarydisplay.PlatformSecondaryDisplay
import com.spela.player.platform.AndroidFileStorage
import com.spela.player.presentation.viewmodel.LibretroButtons
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single {
        val context: android.content.Context = get()
        val dbFile = context.getDatabasePath("spela.db")
        if (dbFile.exists()) {
            try {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                val cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                    null
                )
                val existingTables = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    existingTables.add(cursor.getString(0))
                }
                cursor.close()
                db.close()

                val expectedTables = setOf(
                    "ServerConnectionEntity", "DownloadEntity", "AuthTokenEntity",
                    "PlayHistoryEntity", "ShaderOverrideEntity", "KeyMappingEntity",
                    "GameKeyMappingEntity", "DeviceInfoEntity", "CachedPreferencesEntity",
                    "CachedConsoleEntity", "CachedGameEntity", "LocalSaveStateEntity",
                    "LocalSaveDataEntity",
                )
                val missingTables = expectedTables - existingTables
                if (missingTables.isNotEmpty()) {
                    println("Spela: Database schema incompatible, missing tables: $missingTables.")
                    DatabaseHealthCheck.reportError("Database schema incompatible: missing tables $missingTables. Please reset the app.")
                }
            } catch (e: Exception) {
                println("Spela: Failed to validate database: ${e.message}")
                DatabaseHealthCheck.reportError("Database validation failed: ${e.message}. Please reset the app.")
            }
        }
        DatabaseResetHelper.init(context)
        AndroidSqliteDriver(SpelaDatabase.Schema, context, "spela.db")
    }
    single { SpelaDatabase(get<AndroidSqliteDriver>()) }
    single<HttpClientEngineFactory<*>> { OkHttp }
    single {
        val db = get<SpelaDatabase>()
        SpelaApiClient(
            engineFactory = OkHttp,
            tokenManager = get(),
            onAuthFailure = { reason ->
                get<com.spela.player.data.remote.ConnectivityMonitor>().reportAuthFailure(reason)
            },
            onTokenRefreshed = { accessToken, refreshToken ->
                db.spelaDatabaseQueries.insertTokens(accessToken, refreshToken, "")
            },
        )
    }
    single<FileStorage> { AndroidFileStorage(get()) }
    single { LibretroJni() }
    single<LibretroController> { AndroidLibretroController(get(), get()) }
    single<AchievementsController> { AndroidAchievementsController(get(), get(), get()) }
    single { SecondaryDisplayManager(get()) }
    single<PlatformSecondaryDisplay> {
        AndroidSecondaryDisplay(
            context = get(),
            displayManager = get(),
            scope = get(),
        )
    }
    single {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 15_000
            }
        }
    }

    // Platform default key mapping: retroButtonId -> Android keyCode
    single<Map<Int, Int>>(named("platformDefaultMapping")) {
        mapOf(
            LibretroButtons.UP to KeyEvent.KEYCODE_DPAD_UP,
            LibretroButtons.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            LibretroButtons.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            LibretroButtons.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            LibretroButtons.B to KeyEvent.KEYCODE_BUTTON_A,
            LibretroButtons.A to KeyEvent.KEYCODE_BUTTON_B,
            LibretroButtons.Y to KeyEvent.KEYCODE_BUTTON_X,
            LibretroButtons.X to KeyEvent.KEYCODE_BUTTON_Y,
            LibretroButtons.START to KeyEvent.KEYCODE_BUTTON_START,
            LibretroButtons.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT,
            LibretroButtons.L to KeyEvent.KEYCODE_BUTTON_L1,
            LibretroButtons.R to KeyEvent.KEYCODE_BUTTON_R1,
            LibretroButtons.L2 to KeyEvent.KEYCODE_BUTTON_L2,
            LibretroButtons.R2 to KeyEvent.KEYCODE_BUTTON_R2,
            LibretroButtons.L3 to KeyEvent.KEYCODE_BUTTON_THUMBL,
            LibretroButtons.R3 to KeyEvent.KEYCODE_BUTTON_THUMBR,
        )
    }
}
