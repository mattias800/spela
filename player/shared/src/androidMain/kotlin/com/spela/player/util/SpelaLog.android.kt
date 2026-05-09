package com.spela.player.util

import android.util.Log

actual fun spelaLog(tag: String, message: String) {
    Log.i("Spela", "[$tag] $message")
}
