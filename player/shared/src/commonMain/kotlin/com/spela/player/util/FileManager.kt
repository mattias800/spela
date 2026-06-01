package com.spela.player.util

/**
 * Opens the containing folder of [path] in the OS-native file manager
 * (Explorer / Finder / the default file manager).
 *
 * Returns true if a file manager was launched, false if unsupported or on
 * error. Unsupported on Android, where downloads live in app-private
 * storage that no file manager can browse (#1259).
 */
expect fun revealInFileManager(path: String): Boolean
