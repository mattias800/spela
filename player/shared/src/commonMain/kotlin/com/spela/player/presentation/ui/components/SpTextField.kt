package com.spela.player.presentation.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.theme.spelaBrandGradient

// ─── Text-field architecture ──────────────────────────────────────────────
//
// Every text field in the player app goes through three layers:
//
//   SpTextField (this file, commonMain)
//     └── PlatformTextFieldCore (expect, commonMain)
//           ├── PlatformTextFieldCore.desktop.kt — Material 3 OutlinedTextField
//           └── PlatformTextFieldCore.android.kt — AndroidView { EditText }
//
// SpTextField is the public, cross-platform composable used by every
// screen. It owns:
//   • the optional error message rendered below the field,
//   • the focus-state visual treatment that's shared across platforms:
//     a soft multi-colour glow drawn behind the field, plus a 1.5dp
//     brand-gradient border on its edge.
//
// PlatformTextFieldCore is `expect`/`actual` because the inner editor
// itself has to differ between platforms — Android needs to wrap a real
// `EditText` via `AndroidView` to access standard `EditorInfo` int flags
// (`IME_FLAG_NO_EXTRACT_UI`, `IME_FLAG_NO_FULLSCREEN`) that Compose
// Multiplatform 1.10's KeyboardOptions does not expose. Without those
// flags Gboard and most OEM keyboards take over the entire screen in
// landscape on short-height windows (gaming handhelds like the AYN
// Thor) and the rest of the UI is unusable. See
// `PlatformTextFieldCore.kt` for the full rationale.
//
// The Android implementation also draws the *static* unfocused border,
// the subtle container fill, and the label — Material 3's floating-
// label animation isn't reasonable to recreate around a plain EditText,
// so the label sits as static `Text` above the field. The desktop
// implementation keeps the OutlinedTextField floating-label visual.
//
// Focus tracking gotcha: this wrapper detects focus via
// `Modifier.onFocusChanged { it.hasFocus }`, NOT `it.isFocused`. The
// modifier sits one or two levels above the actual focus target (the
// EditText inside AndroidView, or the OutlinedTextField on desktop),
// so `isFocused` would never fire. `hasFocus` is true for self-or-
// descendant, which gives the same visual result on both platforms.

/**
 * Spela's branded text input. This is the only text-field composable
 * screens should use — never reach for Material 3's `OutlinedTextField`
 * or `BasicTextField` directly, since those bypass the platform-specific
 * IME-flag handling described in [PlatformTextFieldCore].
 *
 * Visual contract:
 *   • Idle field: subtle outlined surface, label rendered above (Android)
 *     or as a floating Material 3 label (desktop).
 *   • Focused field: the multi-colour brand glow + brand-gradient border
 *     drawn by this wrapper, on top of whatever the platform layer
 *     contributes (accent-purple solid border on Android, transparent
 *     border on desktop so the gradient shows cleanly).
 *   • Error: red border drawn by the platform layer; [errorMessage]
 *     rendered as small text below the field.
 *
 * Behaviour contract:
 *   • [imeAction] drives the soft-keyboard action key (Done / Next /
 *     Search / Send / Previous). The platform layer routes the action:
 *     terminal actions dismiss the IME, Next / Previous traverse focus.
 *   • [onImeAction] is invoked for every action firing — including
 *     Next / Previous — for screens that want to react (e.g. trigger
 *     submit). It runs *before* dismiss / focus traversal.
 *   • Tapping outside any field on a screen wrapped in `SpScreen`
 *     clears focus and hides the IME; that behaviour lives in
 *     [SpScreen], not here.
 *
 * @param value Current text value (state-hoisted; no internal state).
 * @param onValueChange Called for every keystroke. Must update [value]
 *   from the screen's state holder for the field to feel responsive.
 * @param modifier Applied to the *outer* Column wrapping the label, the
 *   input row, and the error text. Use this for testTag / weight /
 *   fillMaxWidth.
 * @param label Text rendered above the field on Android, or as the
 *   floating label on desktop. Empty string hides the label.
 * @param placeholder Hint text shown when the field is empty.
 * @param isPassword Mask the input + select a numeric-or-text password
 *   keyboard variant. Note: also influences the [keyboardType] that
 *   actually gets sent to the IME — see the platform implementations.
 * @param isError Render the error border colour. If [errorMessage] is
 *   non-null this is forced to true even when [isError] is false.
 * @param errorMessage Optional error text rendered below the field.
 *   Supplying this also flips the field into the error visual state.
 * @param enabled Disable interaction; renders text in a dimmer colour.
 * @param singleLine Single-line fields convert Enter into the
 *   configured [imeAction]; multiline fields treat Enter as a newline.
 * @param keyboardType Compose KeyboardType — mapped per-platform to
 *   the right native input type (number / email / URI / etc.).
 * @param imeAction Determines the soft-keyboard action key and the
 *   side-effect when fired. Default is [ImeAction.Next] which matches
 *   the typical multi-field form pattern. The *last* field in a form
 *   should use [ImeAction.Done] (or Go / Send / Search) so the last
 *   Enter dismisses the keyboard.
 * @param onImeAction Screen callback fired when the IME action key is
 *   tapped or Enter is pressed on a single-line field.
 * @param leadingIcon Optional composable rendered inside the field on
 *   the leading edge (typical use: search-glass icon).
 * @param trailingIcon Optional composable rendered inside the field on
 *   the trailing edge (typical use: clear-text or visibility-toggle).
 */
@Composable
fun SpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    isPassword: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        // Focus state is tracked here (commonMain) to drive the shared
        // glow + brand-gradient ring. The Android platform layer also
        // tracks focus locally to swap its static border colour to
        // accent-purple. Two state machines, same source of truth (the
        // EditText / OutlinedTextField focus event), but they live in
        // different modules so neither has to know about the other.
        var isFocused by remember { mutableStateOf(false) }
        // The glow + gradient border are skipped for error and disabled
        // states: error gets a solid red border from the platform layer,
        // and disabled fields shouldn't look interactive.
        val showGlow = isFocused && enabled && !isError && errorMessage == null

        PlatformTextFieldCore(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                // `hasFocus`, not `isFocused`: on Android the focusable node
                // is the EditText buried inside AndroidView, so the wrapper's
                // own location never reports `isFocused`. `hasFocus` is true
                // for self-or-descendant, which is what we want here. On
                // desktop (OutlinedTextField is the focus target directly)
                // both flags resolve to the same value. See `feedback_compose_focus_haspower.md`.
                .onFocusChanged { isFocused = it.hasFocus }
                // Outer halo (drawn behind the field). Three soft-blue/
                // purple/pink stops at low alpha render as a faint
                // ambient glow when focused.
                .then(if (showGlow) Modifier.drawBehind {
                    val cr = CornerRadius(SpSpacing.RadiusLarge.toPx())
                    val glowBrush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6B8DD6).copy(alpha = 0.25f),
                            Color(0xFFa855f7).copy(alpha = 0.20f),
                            Color(0xFFE056A0).copy(alpha = 0.15f),
                        ),
                    )
                    drawRoundRect(
                        brush = glowBrush,
                        cornerRadius = cr,
                        size = size,
                        style = Stroke(width = 6.dp.toPx()),
                    )
                } else Modifier)
                // Brand-gradient ring at the field edge. Drawn at the
                // same RoundedCornerShape as the platform-layer borders;
                // since modifier order makes this draw FIRST (chain
                // outermost), the platform layer's solid accent-purple
                // border (Android) will draw on top — accentuating the
                // gradient rather than competing with it.
                .then(if (showGlow) Modifier.border(
                    1.5.dp, spelaBrandGradient(), RoundedCornerShape(SpSpacing.RadiusLarge)
                ) else Modifier),
            enabled = enabled,
            isPassword = isPassword,
            // Force error visual whenever an error message is shown,
            // even if the caller forgot to also set isError = true.
            isError = isError || errorMessage != null,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            label = label,
            placeholder = placeholder,
            keyboardType = keyboardType,
            imeAction = imeAction,
            onImeAction = onImeAction,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = SpTypography.BodySmall,
                color = SpColor.Error,
                modifier = Modifier.padding(start = SpSpacing.Default, top = SpSpacing.XSmall),
            )
        }
    }
}

/**
 * Convenience wrapper around [SpTextField] preconfigured for "search"
 * semantics: single-line, [ImeAction.Search] action key, and
 * [onSearch] is invoked when the user taps the search action key (or
 * presses Enter on a hardware keyboard).
 *
 * No visual differences vs [SpTextField] — this is purely a default-
 * setting helper for screens that need a search input.
 */
@Composable
fun SpSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search games...",
    onSearch: () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    SpTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        singleLine = true,
        imeAction = ImeAction.Search,
        onImeAction = onSearch,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}
