package com.spela.player.util

actual fun spelaLog(tag: String, message: String) {
    println("[Spela:$tag] $message")
}
