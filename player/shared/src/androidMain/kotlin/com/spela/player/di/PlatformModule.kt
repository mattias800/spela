package com.spela.player.di

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.platform.AndroidFileStorage
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import io.ktor.client.engine.okhttp.*
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { SpelaApiClient(OkHttp, get()) }
    single<FileStorage> { AndroidFileStorage(get()) }
    single<LibretroController> { AndroidLibretroController() }
}
