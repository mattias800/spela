package com.spela.player.presentation.ui.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
actual fun createTextClipEntry(text: String): ClipEntry =
    ClipEntry(StringSelection(text))
