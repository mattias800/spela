package com.spela.player.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.controller.AchievementsController
import com.spela.player.libretro.DesktopAchievementsController
import com.spela.player.libretro.DesktopLibretroController
import com.spela.player.libretro.LibretroJni
import com.spela.player.libretro.desktopDefaultRetroMapping
import com.spela.player.platform.DesktopFileStorage
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single {
        val driver = JdbcSqliteDriver("jdbc:sqlite:spela.db")
        SpelaDatabase.Schema.create(driver)
        driver
    }
    single { SpelaDatabase(get<JdbcSqliteDriver>()) }
    single { SpelaApiClient(CIO, get()) }
    single<FileStorage> { DesktopFileStorage() }
    single { LibretroJni() }
    single<LibretroController> { DesktopLibretroController(get()) }
    single<AchievementsController> { DesktopAchievementsController(get(), get(), get()) }
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
