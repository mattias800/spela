package com.spela.player.di

import android.view.KeyEvent
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.spela.player.data.local.DatabaseResetHelper
import com.spela.player.data.local.ExpectedSchema
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.controller.AchievementsController
import com.spela.player.domain.controller.ScreenshotCapture
import com.spela.player.libretro.AndroidAchievementsController
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.libretro.AndroidScreenshotCapture
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
        DatabaseResetHelper.init(context)
        // Create driver first — AndroidSqliteDriver runs migrations automatically
        val driver = AndroidSqliteDriver(SpelaDatabase.Schema, context, "spela.db")
        // Validate schema after migration — auto-reset if incompatible.
        // The local DB is a cache (game metadata, auth tokens, download records).
        // Saves are on the server, so resetting is safe and avoids blocking the user.
        try {
            val errorMessage = validateSchemaWithDriver(driver)
            if (errorMessage != null) {
                println("Spela: $errorMessage — auto-resetting database")
                driver.close()
                context.deleteDatabase("spela.db")
                val freshDriver = AndroidSqliteDriver(SpelaDatabase.Schema, context, "spela.db")
                return@single freshDriver
            }
        } catch (e: Exception) {
            println("Spela: Schema validation failed: ${e.message} — auto-resetting database")
            driver.close()
            context.deleteDatabase("spela.db")
            val freshDriver = AndroidSqliteDriver(SpelaDatabase.Schema, context, "spela.db")
            return@single freshDriver
        }
        driver
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
    single<ScreenshotCapture> { AndroidScreenshotCapture(get<LibretroController>() as AndroidLibretroController) }
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

/**
 * Validates the existing database against [ExpectedSchema].
 * Uses the same [SqlDriver] connection to avoid WAL visibility issues.
 * Returns an error message if incompatible, or null if OK.
 */
private fun validateSchemaWithDriver(driver: SqlDriver): String? {
    val existingTables = driver.executeQuery(
        identifier = null,
        sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
        mapper = { cursor ->
            val tables = mutableSetOf<String>()
            while (cursor.next().value) {
                cursor.getString(0)?.let { tables.add(it) }
            }
            app.cash.sqldelight.db.QueryResult.Value(tables)
        },
        parameters = 0,
    ).value

    val missingTables = ExpectedSchema.tables.keys - existingTables
    if (missingTables.isNotEmpty()) {
        return "Database schema incompatible: missing tables $missingTables."
    }

    // Check every table's columns
    for ((table, expectedColumns) in ExpectedSchema.tables) {
        val actualColumns = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { cursor ->
                val cols = mutableSetOf<String>()
                while (cursor.next().value) {
                    cursor.getString(1)?.let { cols.add(it) }
                }
                app.cash.sqldelight.db.QueryResult.Value(cols)
            },
            parameters = 0,
        ).value
        val missingColumns = expectedColumns - actualColumns
        if (missingColumns.isNotEmpty()) {
            return "Database schema incompatible: table $table missing columns $missingColumns."
        }
    }

    return null
}
