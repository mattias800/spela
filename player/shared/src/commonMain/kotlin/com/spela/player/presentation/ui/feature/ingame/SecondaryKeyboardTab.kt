package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spela.player.presentation.ui.theme.SpColor

/**
 * Keyboard layer identifiers.
 */
private enum class KeyboardLayer {
    QWERTY, FN, SYMBOLS, PLATFORM,
}

/**
 * A single key definition for the on-screen keyboard.
 */
private data class KeyDef(
    val label: String,
    val retroKey: Int,
    val widthWeight: Float = 1f,
)

/**
 * Virtual keyboard tab for the secondary screen controls page.
 * Uses a layer system: QWERTY (default), Fn, Symbols, plus
 * optional per-platform layers for special keys.
 */
@Composable
fun SecondaryKeyboardTab(
    consoleId: String,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeLayer by remember { mutableStateOf(KeyboardLayer.QWERTY) }
    var shiftActive by remember { mutableStateOf(false) }
    var capsLock by remember { mutableStateOf(false) }
    val effectiveShift = shiftActive || capsLock

    // Modifier keys (sticky behavior)
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    val platformLayer = getPlatformLayerKeys(consoleId)
    val hasPlatformLayer = platformLayer != null

    val onKey: (Int, Boolean) -> Unit = remember(onKeyDown, onKeyUp) {
        { key: Int, pressed: Boolean ->
            if (pressed) onKeyDown(key) else onKeyUp(key)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .semantics { contentDescription = "Virtual keyboard, ${activeLayer.name} layer" },
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Key rows for the active layer
        val rows = when (activeLayer) {
            KeyboardLayer.QWERTY -> getQwertyRows(effectiveShift)
            KeyboardLayer.FN -> getFnRows()
            KeyboardLayer.SYMBOLS -> getSymbolRows()
            KeyboardLayer.PLATFORM -> platformLayer ?: getQwertyRows(effectiveShift)
        }

        rows.forEach { row ->
            KeyRow(keys = row, onKey = onKey)
        }

        // Bottom control row: modifiers + layer switches + space + enter
        BottomControlRow(
            activeLayer = activeLayer,
            shiftActive = effectiveShift,
            ctrlActive = ctrlActive,
            altActive = altActive,
            hasPlatformLayer = hasPlatformLayer,
            platformName = getPlatformLayerName(consoleId),
            onLayerSwitch = { layer ->
                activeLayer = if (activeLayer == layer) KeyboardLayer.QWERTY else layer
            },
            onShiftToggle = {
                shiftActive = !shiftActive
                if (shiftActive) onKeyDown(RetroKey.LSHIFT) else onKeyUp(RetroKey.LSHIFT)
            },
            onCtrlToggle = {
                ctrlActive = !ctrlActive
                if (ctrlActive) onKeyDown(RetroKey.LCTRL) else onKeyUp(RetroKey.LCTRL)
            },
            onAltToggle = {
                altActive = !altActive
                if (altActive) onKeyDown(RetroKey.LALT) else onKeyUp(RetroKey.LALT)
            },
            onKey = onKey,
        )
    }
}

@Composable
private fun KeyRow(
    keys: List<KeyDef>,
    onKey: (Int, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        keys.forEach { keyDef ->
            KeyButton(
                keyDef = keyDef,
                onKey = onKey,
                modifier = Modifier.weight(keyDef.widthWeight),
            )
        }
    }
}

@Composable
private fun KeyButton(
    keyDef: KeyDef,
    onKey: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onKey(keyDef.retroKey, isPressed)
    }

    val bgColor = if (isPressed) SpColor.Primary else SpColor.SurfaceVariant

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(interactionSource = interactionSource, indication = null) { }
            .semantics { contentDescription = "Key ${keyDef.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = keyDef.label,
            fontSize = 11.sp,
            color = SpColor.OnBackground,
        )
    }
}

@Composable
private fun BottomControlRow(
    activeLayer: KeyboardLayer,
    shiftActive: Boolean,
    ctrlActive: Boolean,
    altActive: Boolean,
    hasPlatformLayer: Boolean,
    platformName: String?,
    onLayerSwitch: (KeyboardLayer) -> Unit,
    onShiftToggle: () -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onKey: (Int, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ModifierPill(label = "Shift", isActive = shiftActive, onClick = onShiftToggle, modifier = Modifier.weight(1.2f))
        ModifierPill(label = "Ctrl", isActive = ctrlActive, onClick = onCtrlToggle, modifier = Modifier.weight(1f))
        ModifierPill(label = "Alt", isActive = altActive, onClick = onAltToggle, modifier = Modifier.weight(1f))
        LayerPill(label = "Fn", isActive = activeLayer == KeyboardLayer.FN, onClick = { onLayerSwitch(KeyboardLayer.FN) }, modifier = Modifier.weight(0.8f))
        LayerPill(label = "Sym", isActive = activeLayer == KeyboardLayer.SYMBOLS, onClick = { onLayerSwitch(KeyboardLayer.SYMBOLS) }, modifier = Modifier.weight(0.8f))
        if (hasPlatformLayer && platformName != null) {
            LayerPill(label = platformName, isActive = activeLayer == KeyboardLayer.PLATFORM, onClick = { onLayerSwitch(KeyboardLayer.PLATFORM) }, modifier = Modifier.weight(1f))
        }
        KeyButton(keyDef = KeyDef("Space", RetroKey.SPACE), onKey = onKey, modifier = Modifier.weight(2f))
        KeyButton(keyDef = KeyDef("Ent", RetroKey.RETURN), onKey = onKey, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ModifierPill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isActive) SpColor.Primary else SpColor.SurfaceVariant
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label ${if (isActive) "active" else "inactive"}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 10.sp, color = SpColor.OnBackground)
    }
}

@Composable
private fun LayerPill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isActive) SpColor.Primary.copy(alpha = 0.7f) else SpColor.SurfaceVariant.copy(alpha = 0.6f)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label layer ${if (isActive) "active" else "inactive"}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 10.sp, color = SpColor.OnBackground)
    }
}

// --- Key layout definitions ---

private fun getQwertyRows(shift: Boolean): List<List<KeyDef>> {
    val nums = listOf(
        KeyDef("1", RetroKey.KEY_1), KeyDef("2", RetroKey.KEY_2), KeyDef("3", RetroKey.KEY_3),
        KeyDef("4", RetroKey.KEY_4), KeyDef("5", RetroKey.KEY_5), KeyDef("6", RetroKey.KEY_6),
        KeyDef("7", RetroKey.KEY_7), KeyDef("8", RetroKey.KEY_8), KeyDef("9", RetroKey.KEY_9),
        KeyDef("0", RetroKey.KEY_0),
    )
    val topRow = "QWERTYUIOP".map { c ->
        val label = if (shift) c.uppercase() else c.lowercase()
        KeyDef(label, RetroKey.A + (c.lowercaseChar() - 'a'))
    }
    val middleRow = "ASDFGHJKL".map { c ->
        val label = if (shift) c.uppercase() else c.lowercase()
        KeyDef(label, RetroKey.A + (c.lowercaseChar() - 'a'))
    }
    val bottomRow = buildList {
        "ZXCVBNM".forEach { c ->
            val label = if (shift) c.uppercase() else c.lowercase()
            add(KeyDef(label, RetroKey.A + (c.lowercaseChar() - 'a')))
        }
        add(KeyDef("\u232B", RetroKey.BACKSPACE, 1.5f))
    }
    return listOf(nums, topRow, middleRow, bottomRow)
}

private fun getFnRows(): List<List<KeyDef>> = listOf(
    listOf(
        KeyDef("F1", RetroKey.F1), KeyDef("F2", RetroKey.F2), KeyDef("F3", RetroKey.F3),
        KeyDef("F4", RetroKey.F4), KeyDef("F5", RetroKey.F5), KeyDef("F6", RetroKey.F6),
    ),
    listOf(
        KeyDef("F7", RetroKey.F7), KeyDef("F8", RetroKey.F8), KeyDef("F9", RetroKey.F9),
        KeyDef("F10", RetroKey.F10), KeyDef("F11", RetroKey.F11), KeyDef("F12", RetroKey.F12),
    ),
    listOf(
        KeyDef("Esc", RetroKey.ESCAPE), KeyDef("Tab", RetroKey.TAB),
        KeyDef("Ins", RetroKey.INSERT), KeyDef("Del", RetroKey.DELETE),
    ),
    listOf(
        KeyDef("Home", RetroKey.HOME), KeyDef("End", RetroKey.END),
        KeyDef("PgUp", RetroKey.PAGEUP), KeyDef("PgDn", RetroKey.PAGEDOWN),
    ),
    listOf(
        KeyDef("\u2190", RetroKey.LEFT), KeyDef("\u2191", RetroKey.UP),
        KeyDef("\u2193", RetroKey.DOWN), KeyDef("\u2192", RetroKey.RIGHT),
    ),
)

private fun getSymbolRows(): List<List<KeyDef>> = listOf(
    listOf(
        KeyDef("!", RetroKey.EXCLAIM), KeyDef("@", RetroKey.AT), KeyDef("#", RetroKey.HASH),
        KeyDef("$", RetroKey.DOLLAR), KeyDef("%", RetroKey.KEY_5),
        KeyDef("^", RetroKey.CARET), KeyDef("&", RetroKey.AMPERSAND),
        KeyDef("*", RetroKey.ASTERISK), KeyDef("(", RetroKey.LEFTPAREN),
        KeyDef(")", RetroKey.RIGHTPAREN),
    ),
    listOf(
        KeyDef("-", RetroKey.MINUS), KeyDef("=", RetroKey.EQUALS),
        KeyDef("[", RetroKey.LEFTBRACKET), KeyDef("]", RetroKey.RIGHTBRACKET),
        KeyDef("\\", RetroKey.BACKSLASH),
        KeyDef(";", RetroKey.SEMICOLON), KeyDef("'", RetroKey.QUOTE),
    ),
    listOf(
        KeyDef("`", RetroKey.BACKQUOTE), KeyDef("~", RetroKey.TILDE),
        KeyDef("<", RetroKey.LESS), KeyDef(">", RetroKey.GREATER),
        KeyDef("/", RetroKey.SLASH), KeyDef("?", RetroKey.QUESTION),
        KeyDef(",", RetroKey.COMMA), KeyDef(".", RetroKey.PERIOD),
    ),
    listOf(
        KeyDef("+", RetroKey.PLUS), KeyDef("_", RetroKey.UNDERSCORE),
        KeyDef(":", RetroKey.COLON), KeyDef("\"", RetroKey.QUOTEDBL),
        KeyDef("\u232B", RetroKey.BACKSPACE, 1.5f),
    ),
)

// --- Per-platform layers ---

private fun getPlatformLayerName(consoleId: String): String? = when (consoleId.lowercase()) {
    "amiga", "ademo" -> "Amiga"
    "dos", "ddemo" -> "DOS"
    "c64" -> "C64"
    "c128" -> "C128"
    "vic20" -> "VIC"
    "msx", "msx2" -> "MSX"
    else -> null
}

private fun getPlatformLayerKeys(consoleId: String): List<List<KeyDef>>? = when (consoleId.lowercase()) {
    "amiga", "ademo" -> listOf(
        listOf(KeyDef("L Amiga", RetroKey.LSUPER), KeyDef("R Amiga", RetroKey.RSUPER), KeyDef("Help", RetroKey.HELP)),
        listOf(KeyDef("F1", RetroKey.F1), KeyDef("F2", RetroKey.F2), KeyDef("F3", RetroKey.F3), KeyDef("F4", RetroKey.F4), KeyDef("F5", RetroKey.F5)),
        listOf(KeyDef("F6", RetroKey.F6), KeyDef("F7", RetroKey.F7), KeyDef("F8", RetroKey.F8), KeyDef("F9", RetroKey.F9), KeyDef("F10", RetroKey.F10)),
    )
    "dos", "ddemo" -> listOf(
        listOf(KeyDef("Esc", RetroKey.ESCAPE), KeyDef("Tab", RetroKey.TAB), KeyDef("Del", RetroKey.DELETE)),
        listOf(KeyDef("F1", RetroKey.F1), KeyDef("F2", RetroKey.F2), KeyDef("F3", RetroKey.F3), KeyDef("F4", RetroKey.F4), KeyDef("F5", RetroKey.F5), KeyDef("F6", RetroKey.F6)),
        listOf(KeyDef("\u2190", RetroKey.LEFT), KeyDef("\u2191", RetroKey.UP), KeyDef("\u2193", RetroKey.DOWN), KeyDef("\u2192", RetroKey.RIGHT)),
    )
    "c64" -> listOf(
        listOf(KeyDef("RUN/STOP", RetroKey.ESCAPE), KeyDef("RESTORE", RetroKey.PAGEUP)),
        listOf(KeyDef("C=", RetroKey.LCTRL), KeyDef("CTRL", RetroKey.TAB)),
        listOf(KeyDef("\u2190", RetroKey.LEFT), KeyDef("\u2191", RetroKey.UP), KeyDef("\u2193", RetroKey.DOWN), KeyDef("\u2192", RetroKey.RIGHT)),
    )
    "c128" -> listOf(
        listOf(KeyDef("RUN/STOP", RetroKey.ESCAPE), KeyDef("RESTORE", RetroKey.PAGEUP)),
        listOf(KeyDef("C=", RetroKey.LCTRL), KeyDef("CTRL", RetroKey.TAB), KeyDef("40/80", RetroKey.F12)),
        listOf(KeyDef("\u2190", RetroKey.LEFT), KeyDef("\u2191", RetroKey.UP), KeyDef("\u2193", RetroKey.DOWN), KeyDef("\u2192", RetroKey.RIGHT)),
    )
    "vic20" -> listOf(
        listOf(KeyDef("RUN/STOP", RetroKey.ESCAPE), KeyDef("RESTORE", RetroKey.PAGEUP)),
        listOf(KeyDef("C=", RetroKey.LCTRL)),
    )
    "msx", "msx2" -> listOf(
        listOf(KeyDef("SELECT", RetroKey.F1), KeyDef("STOP", RetroKey.F2)),
        listOf(KeyDef("GRAPH", RetroKey.F3), KeyDef("CODE", RetroKey.F4)),
        listOf(KeyDef("\u2190", RetroKey.LEFT), KeyDef("\u2191", RetroKey.UP), KeyDef("\u2193", RetroKey.DOWN), KeyDef("\u2192", RetroKey.RIGHT)),
    )
    else -> null
}
