package com.spela.player.util

/**
 * No folder picker on Android: choosing an arbitrary download destination
 * needs the Storage Access Framework (a per-Activity result flow), which is
 * out of scope. Returns null so the caller keeps the default behavior. (#1257)
 */
actual fun pickDirectory(title: String): String? = null
