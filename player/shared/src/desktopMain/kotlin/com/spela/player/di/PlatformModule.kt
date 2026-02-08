package com.spela.player.di

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.libretro.DesktopLibretroController
import com.spela.player.platform.DesktopFileStorage
import com.spela.player.presentation.viewmodel.LibretroController
import com.spela.player.util.FileStorage
import io.ktor.client.engine.cio.*
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { SpelaApiClient(CIO, get()) }
    single<FileStorage> { DesktopFileStorage() }
    single<LibretroController> { DesktopLibretroController() }
}
