package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Inner text-input widget used by [SpTextField]. Split out as
 * `expect`/`actual` so Android can render an `AndroidView`-wrapped
 * `EditText` with `IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN`
 * set on its `imeOptions` — the only reliable way to keep the
 * keyboard from going fullscreen on landscape Android handhelds
 * (AYN Thor and similar).
 *
 * Compose Multiplatform 1.10's `PlatformImeOptions` only exposes
 * `privateImeOptions` (a free-form string). Gboard and most OEM
 * keyboards ignore string hints for fullscreen behaviour — they only
 * honour the standard `EditorInfo.imeOptions` int flags. Compose
 * doesn't expose those flags through its public Kotlin API, so we
 * have to drop down to the Android view system on Android only.
 *
 * Desktop and other targets keep using `OutlinedTextField` with no
 * behaviour change — fullscreen IME is a landscape-Android-handheld
 * problem only.
 */
@Composable
expect fun PlatformTextFieldCore(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    isPassword: Boolean,
    isError: Boolean,
    singleLine: Boolean,
    minLines: Int,
    maxLines: Int,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
)
