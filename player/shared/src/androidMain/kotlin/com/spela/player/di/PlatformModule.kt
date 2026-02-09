package com.spela.player.di

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.platform.AndroidFileStorage
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { SpelaApiClient(OkHttp, get()) }
    single<FileStorage> { AndroidFileStorage(get()) }
    single<LibretroController> { AndroidLibretroController(get()) }
    single {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 15_000
            }
        }
    }
}
