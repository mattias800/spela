package com.spela.player.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.libretro.DesktopLibretroController
import com.spela.player.platform.DesktopFileStorage
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import org.koin.core.module.Module
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
    single<LibretroController> { DesktopLibretroController() }
    single {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 15_000
            }
        }
    }
}
