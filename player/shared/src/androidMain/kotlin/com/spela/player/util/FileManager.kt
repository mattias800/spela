package com.spela.player.util

/**
 * No-op on Android: downloads live in app-private internal storage
 * (context.filesDir), which the system file manager can't browse, so there
 * is nothing to reveal. The UI hides the "Show in folder" action on Android.
 * (#1259)
 */
actual fun revealInFileManager(path: String): Boolean = false
