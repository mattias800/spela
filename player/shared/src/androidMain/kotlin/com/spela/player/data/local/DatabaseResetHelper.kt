package com.spela.player.data.local

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("StaticFieldLeak")
actual object DatabaseResetHelper {
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    actual fun resetDatabase() {
        context?.deleteDatabase("spela.db")
        DatabaseHealthCheck.clearError()
    }
}
