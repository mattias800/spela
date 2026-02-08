package com.spela.player.android

import android.app.Application
import com.spela.player.di.commonModule
import com.spela.player.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SpelaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SpelaApplication)
            modules(commonModule, platformModule())
        }
    }
}
