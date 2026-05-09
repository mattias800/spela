package com.spela.player.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * The platform-specific inner editor used by [SpTextField]. Don't call
 * this directly from screens — go through [SpTextField] so the wrapper's
 * focus glow, error message, and shared visual contract apply.
 *
 * ## Why this is `expect`/`actual`
 *
 * Android needs to wrap a real `EditText` via `AndroidView` so it can
 * set the standard `EditorInfo.imeOptions` int flags
 * (`IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN`) on the input
 * connection. Compose Multiplatform 1.10's `PlatformImeOptions` only
 * exposes `privateImeOptions` (a free-form string hint), and Gboard
 * plus most OEM keyboards ignore string hints for the fullscreen
 * extract-mode decision — they only look at the int flags.
 *
 * Without those flags, when a Compose `BasicTextField` / `OutlinedTextField`
 * gains focus on a short-height landscape window (gaming handhelds
 * like the AYN Thor, secondary displays, etc.), the IME goes fullscreen
 * and renders its own giant text box on top of the entire UI — making
 * the field unusable, and also masking everything from UiAutomator-
 * based instrumentation tests.
 *
 * Desktop and other non-Android targets don't have this problem (no
 * concept of fullscreen extract mode), so the desktop actual just
 * delegates to Material 3 `OutlinedTextField` with the wrapper's focus
 * styling overlaid.
 *
 * ## Per-platform responsibilities
 *
 * Both implementations:
 *   • Render the actual editor (text input area).
 *   • Map `keyboardType` and `imeAction` to the platform-native type
 *     and action constants.
 *   • Invoke `onImeAction` when the user taps the soft-keyboard's
 *     action key (Done / Next / Search / etc.) or presses Enter on a
 *     single-line field.
 *   • Honour `enabled`, `isPassword`, `singleLine`, `minLines`,
 *     `maxLines`, `leadingIcon`, `trailingIcon` exactly as documented
 *     on [SpTextField].
 *
 * Android-specific extras (because the wrapper's Material-3-shaped
 * primitives don't apply to a raw EditText):
 *   • Render the [label] as static `Text` above the input row (no
 *     floating-label animation — recreating Material 3's measurement
 *     and animation precisely around an EditText would be a lot of
 *     code for marginal value).
 *   • Draw a static unfocused border and subtle container fill so
 *     the field reads as a contained surface even when the wrapper's
 *     focus glow isn't active. Switches to accent-purple on focus to
 *     give a clear focused state.
 *   • For `ImeAction.Done / Go / Search / Send`, dismiss the IME and
 *     clear focus. For `Next / Previous`, advance focus via Android's
 *     focus-traverse — the keyboard stays up because the next field
 *     immediately needs it.
 *
 * Desktop-specific extras:
 *   • Use OutlinedTextField's built-in floating-label animation —
 *     it's free and idiomatic on desktop.
 *   • Set `focusedBorderColor = Transparent` so the wrapper's
 *     brand-gradient border is the only ring visible on focus.
 *
 * @param modifier The modifier from [SpTextField] — already includes
 *   `fillMaxWidth`, focus tracking, and the conditional focus glow +
 *   gradient ring. The platform implementation should apply this to
 *   the *editor row*, not the outer label-plus-field column, so the
 *   gradient ring traces the field rather than the label too.
 * @param label See [SpTextField.label]. Empty string means "no label".
 * @param onImeAction Invoked unconditionally for every action firing,
 *   *before* the platform layer performs dismiss / focus-traverse.
 *   Screens use this to trigger submit / search / etc. — and the
 *   side-effect (dismiss / next field) is automatic.
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
