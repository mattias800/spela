package com.spela.player.presentation.ui.components

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun createTextClipEntry(text: String): ClipEntry =
    ClipEntry(ClipData.newPlainText("plain text", text))
