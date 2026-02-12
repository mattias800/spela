package com.spela.player.di

import android.view.KeyEvent
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
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
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { AndroidSqliteDriver(SpelaDatabase.Schema, get(), "spela.db") }
    single { SpelaDatabase(get<AndroidSqliteDriver>()) }
    single { SpelaApiClient(OkHttp, get()) }
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
