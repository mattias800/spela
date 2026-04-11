package com.spela.player.presentation.ui.components

import androidx.compose.ui.platform.ClipEntry

/**
 * Creates a platform-specific [ClipEntry] from a plain-text string.
 *
 * Needed because [ClipEntry] is `expect class`: its constructor takes a
 * native object (`android.content.ClipData` on Android,
 * `java.awt.datatransfer.Transferable` on desktop) which can't be
 * constructed from commonMain code. Compose 1.10 has an internal
 * `AnnotatedString?.toClipEntry()` helper inside `compose-foundation`
 * but it's `internal`, so we re-implement the same idea here.
 *
 * Use in combination with `androidx.compose.ui.platform.LocalClipboard`
 * and `Clipboard.setClipEntry(...)`.
 */
expect fun createTextClipEntry(text: String): ClipEntry
