package com.spela.player.presentation.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Android actual for [PlatformTextFieldCore].
 *
 * ## Architecture
 *
 * ```
 *  Column (Modifier.fillMaxWidth)                  <- our wrapper for label + field
 *    ├── Text(label)                               <- static label, when label.isNotEmpty()
 *    └── Row (modifier from SpTextField wrapper)   <- the field "edge"
 *          ├── leadingIcon (optional)
 *          ├── Box.weight(1f)
 *          │     └── AndroidView { EditText }      <- the focusable text editor
 *          └── trailingIcon (optional)
 * ```
 *
 * The Row receives the modifier passed in from [SpTextField], so the
 * wrapper's focus glow + brand-gradient ring (when focused) trace
 * around the *field row only*, not the label above. We add our own
 * static border + container fill onto the same Row.
 *
 * The `EditText` itself is bare — `background = null`, no drawable —
 * because every visual treatment happens in Compose at the Row level.
 * This avoids the rendering glitches that arise when a Drawable border
 * and a Compose `Modifier.border` both try to paint at the same edge.
 *
 * ## Why an EditText instead of OutlinedTextField
 *
 * To set `EditorInfo.IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN`.
 * Compose Multiplatform 1.10 exposes only `privateImeOptions` (a free-
 * form String) on Android via `PlatformImeOptions`, and Gboard / OEM
 * keyboards ignore string hints when deciding whether to enter
 * fullscreen extract mode. Without those int flags, the IME takes
 * over the entire screen on short-height landscape windows — the
 * AYN Thor, the second screen of dual-screen handhelds, secondary
 * displays — and the UI behind is hidden. See
 * `PlatformTextFieldCore.kt` (commonMain) for the upstream rationale.
 *
 * If a future Compose version exposes the int flags directly through
 * `KeyboardOptions` / `PlatformImeOptions`, this entire file can be
 * deleted and the Android target can fall back to `OutlinedTextField`
 * the same way desktop does. Until then: this is the workaround.
 *
 * ## Visual treatment
 *
 * | State           | Border                 | Background          | Outer ring (wrapper)              |
 * |-----------------|------------------------|---------------------|-----------------------------------|
 * | Idle            | white @ 40%, 1.5dp     | white @ 4%          | none                              |
 * | Focused         | accent-purple, 2.5dp   | accent-purple @ 10% | brand-gradient ring + soft glow   |
 * | Error           | error red, 1.5dp       | white @ 4%          | none (error replaces focus glow)  |
 * | Disabled        | white @ 40%, 1.5dp     | white @ 4%          | none                              |
 *
 * Idle vs focused for the *static* part is computed locally in this
 * file (see [fieldFocused]). The wrapper's ring/glow is computed in
 * [SpTextField] from a *different* focus-state flag. Both flags read
 * the same source-of-truth (the EditText's focus event surfaced
 * through Compose), via `Modifier.onFocusChanged { it.hasFocus }`.
 * Note `hasFocus`, not `isFocused`: the focusable target is the
 * EditText buried inside AndroidView, several levels below the
 * modifier owner — `isFocused` would never fire.
 *
 * ## IME action handling
 *
 * The soft-keyboard action key — and Enter on a single-line field,
 * which TextView routes through onEditorAction — invokes
 * [handleImeAction]. The mapping:
 *   • `Done / Go / Search / Send` → run screen callback, dismiss IME,
 *     clear focus.
 *   • `Next / Previous`           → run screen callback, advance focus
 *     via Android's `focusSearch`. Keyboard stays up.
 *   • `None`                      → run screen callback, no UI side-effect.
 *
 * We have to perform every side-effect manually because returning
 * `true` from `setOnEditorActionListener` consumes the event and
 * suppresses Android's defaults. Returning `false` from the listener
 * to let Android handle defaults is unreliable inside Compose's
 * focus tree (focusSearch doesn't always find the next field across
 * AndroidView boundaries), so we keep the listener fully responsible.
 */
@Composable
actual fun PlatformTextFieldCore(
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
) {
    // Pre-resolve theme colours into ARGB ints for the EditText (which
    // doesn't accept Compose Color directly). Re-resolved on every
    // recomposition so theme/enabled changes take effect.
    val textColorArgb = if (enabled) SpColor.OnBackground.toArgb()
    else SpColor.OnBackgroundTertiary.toArgb()
    val hintColorArgb = SpColor.OnBackgroundTertiary.toArgb()
    val cursorColorArgb = SpColor.AccentPurple.toArgb()

    // Static-border focus tracking. Independent from SpTextField's
    // (which drives the brand-gradient ring on top); see file-level
    // KDoc for why we maintain two separate flags. `hasFocus` because
    // the EditText is several levels below this modifier owner.
    var fieldFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isError -> SpColor.Error
        fieldFocused -> SpColor.AccentPurple
        else -> Color.White.copy(alpha = 0.40f)
    }
    val borderWidth = if (fieldFocused && !isError) 2.5.dp else 1.5.dp
    val containerColor = if (fieldFocused && !isError) {
        SpColor.AccentPurple.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.04f)
    }
    val containerShape = RoundedCornerShape(SpSpacing.RadiusLarge)

    // Outer Column: holds the static label above the field. Note this
    // Column does NOT receive the SpTextField wrapper modifier — that
    // goes on the inner Row, so the wrapper's focus glow / brand ring
    // traces the field edge, not the label area.
    Column(modifier = Modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = SpTypography.LabelMedium,
                color = if (enabled) SpColor.OnBackgroundSecondary
                else SpColor.OnBackgroundTertiary,
                // Tiny start indent so the label optically aligns with
                // the editor text (the Row's horizontal padding pushes
                // the editor in by SpSpacing.Default).
                modifier = Modifier.padding(
                    start = SpSpacing.XSmall,
                    bottom = SpSpacing.XSmall,
                ),
            )
        }
        Row(
            // Modifier order matters here.
            //
            //   modifier (from SpTextField wrapper):
            //     fillMaxWidth → onFocusChanged → drawBehind glow (focus)
            //                  → Modifier.border(brand gradient) (focus)
            //   .onFocusChanged { fieldFocused = ... }     ← our local focus mirror
            //   .background(containerColor, containerShape) ← container fill
            //   .border(borderWidth, borderColor, ..)       ← static border
            //   .clip(containerShape)                       ← clip child content
            //   .heightIn(min = 56.dp)                      ← MD3-equivalent min height
            //   .padding(horizontal = SpSpacing.Default)    ← inner gutter
            //
            // The wrapper's brand-gradient `Modifier.border` runs first
            // in the chain (i.e. drawn underneath); our static border
            // runs later and draws on top. When the field is focused the
            // wrapper's gradient and our solid accent-purple coexist —
            // the gradient peeks out at the corners, the solid line
            // dominates the straight edges. That's the intended look.
            modifier = modifier
                // `hasFocus`, not `isFocused` — see file-level KDoc.
                .onFocusChanged { fieldFocused = it.hasFocus }
                .background(containerColor, containerShape)
                .border(borderWidth, borderColor, containerShape)
                .clip(containerShape)
                .heightIn(min = 56.dp)
                .padding(horizontal = SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Box(modifier = Modifier.padding(end = SpSpacing.Small)) { leadingIcon() }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    // factory: one-time setup. Things that DON'T change
                    // across recompositions (listeners, cursor drawable,
                    // padding, gravity) live here. State that the screen
                    // can change (text value, hint, isError, imeAction,
                    // inputType) is re-applied in the `update` block
                    // below — never trust a closure to capture the
                    // latest value, since the factory runs only once.
                    factory = { ctx ->
                        EditText(ctx).apply {
                        // Bare editor: container fill + border are drawn
                        // at the Row level in Compose, not here.
                        background = null
                        setTextColor(textColorArgb)
                        setHintTextColor(hintColorArgb)
                        textSize = 16f
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        // `setMinLines` / `setMaxLines` (not the
                        // function parameters) — the EditText properties
                        // are shadowed in this scope by the @Composable
                        // params of the same name.
                        isSingleLine = singleLine
                        if (singleLine) {
                            setMaxLines(1)
                        } else {
                            setMinLines(minLines)
                            setMaxLines(maxLines)
                        }
                        inputType = computeAndroidInputType(
                            isPassword,
                            keyboardType,
                            singleLine,
                        )
                        if (isPassword) {
                            transformationMethod = PasswordTransformationMethod.getInstance()
                        }
                        // The whole reason this file exists: set the
                        // standard `EditorInfo` int flags so Gboard / OEM
                        // keyboards skip fullscreen extract mode. See
                        // file-level KDoc.
                        imeOptions = imeActionToEditorInfo(imeAction) or
                            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                            EditorInfo.IME_FLAG_NO_FULLSCREEN
                        // Row supplies horizontal gutter; vertical pad
                        // here keeps text off the top/bottom edges. No
                        // horizontal pad → text aligns with the label
                        // start indent above.
                        val verticalPad = (SpSpacing.Small.value * resources.displayMetrics.density).toInt()
                        setPadding(0, verticalPad, 0, verticalPad)
                        // Accent-coloured cursor (API 29+). Pre-Q falls
                        // back to the system default.
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            textCursorDrawable = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                setSize((2 * resources.displayMetrics.density).toInt(), 0)
                                setColor(cursorColorArgb)
                            }
                        }
                        // Hint = placeholder. The label sits above the
                        // field as a separate Text composable, since we
                        // don't get Material 3's floating-label animation
                        // around a raw EditText.
                        hint = placeholder
                        // Routes the action key (and Enter on single-line
                        // fields, which TextView converts to an editor
                        // action) through `handleImeAction`. We always
                        // return `true` to fully take ownership of the
                        // side-effect; see file-level KDoc.
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == imeActionToEditorInfo(imeAction) ||
                                actionId == EditorInfo.IME_ACTION_DONE) {
                                handleImeAction(this, imeAction, onImeAction)
                                true
                            } else false
                        }
                        // Surface text changes back to Compose state.
                        // Guarded inside the watcher (newText != value)
                        // so we don't loop when our own update block
                        // calls setText below.
                        addTextChangedListener(SimpleTextWatcher { newText ->
                            if (newText != value) onValueChange(newText)
                        })
                    }
                },
                // update: runs on every recomposition. Only mutate
                // properties that can change AT ALL across the field's
                // life — and gate the mutations behind equality checks
                // so we don't bounce the cursor / IME composition on
                // every keystroke (Compose recomposes after each value
                // update, which would otherwise re-set the text and
                // re-trigger the change watcher).
                update = { editText ->
                    if (editText.text.toString() != value) {
                        val cursor = editText.selectionStart
                        editText.setText(value)
                        editText.setSelection(cursor.coerceIn(0, value.length))
                    }
                    editText.isEnabled = enabled
                    editText.hint = placeholder
                    // imeOptions is cheap to set — re-applied each frame
                    // because the action might change as the screen
                    // re-renders for some reason.
                    editText.imeOptions = imeActionToEditorInfo(imeAction) or
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                        EditorInfo.IME_FLAG_NO_FULLSCREEN
                    val newInputType = computeAndroidInputType(
                        isPassword,
                        keyboardType,
                        singleLine,
                    )
                    if (editText.inputType != newInputType) {
                        editText.inputType = newInputType
                        if (isPassword) {
                            editText.transformationMethod =
                                PasswordTransformationMethod.getInstance()
                        }
                    }
                },
            )
        }

            if (trailingIcon != null) {
                Box(modifier = Modifier.padding(start = SpSpacing.Small)) { trailingIcon() }
            }
        }
    }
}

/**
 * `TextWatcher` that only forwards the final post-edit text and
 * ignores the verbose before/on intermediate callbacks. Used to bridge
 * EditText text changes back into Compose state via `onValueChange`.
 */
private class SimpleTextWatcher(
    private val onChange: (String) -> Unit,
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) {
        onChange(s?.toString().orEmpty())
    }
}

/**
 * Map a Compose [ImeAction] to the corresponding Android
 * [EditorInfo] action constant — what we put in the EditText's
 * `imeOptions` so the soft keyboard renders the right action key
 * (Done check / Search magnifier / Next arrow / etc.).
 *
 * Unknown / future Compose actions fall back to `IME_ACTION_DONE`.
 */
private fun imeActionToEditorInfo(imeAction: ImeAction): Int = when (imeAction) {
    ImeAction.Default, ImeAction.Done -> EditorInfo.IME_ACTION_DONE
    ImeAction.Go -> EditorInfo.IME_ACTION_GO
    ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
    ImeAction.Previous -> EditorInfo.IME_ACTION_PREVIOUS
    ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
    ImeAction.Send -> EditorInfo.IME_ACTION_SEND
    ImeAction.None -> EditorInfo.IME_ACTION_NONE
    else -> EditorInfo.IME_ACTION_DONE
}

/**
 * Side-effect handler for an IME-action trigger. Called from the
 * EditText's `OnEditorActionListener` for both soft-keyboard action
 * key taps and hardware Enter on single-line fields.
 *
 * Routing:
 *   • `Done / Go / Search / Send` — invoke the screen's [onImeAction],
 *     then clear focus + hide the IME via `InputMethodManager`. The
 *     screen typically handles submit / search inside [onImeAction];
 *     dismissing the keyboard afterwards is automatic.
 *   • `Next` — invoke [onImeAction], then forward-focus via
 *     `focusSearch(FOCUS_FORWARD)`. Keyboard intentionally stays up
 *     because the next field needs it.
 *   • `Previous` — symmetric to Next, backward.
 *   • `None` — invoke [onImeAction], no UI side-effect.
 *
 * Why we own every side-effect: the listener must return `true` to
 * tell EditText the event is handled. That in turn suppresses
 * Android's default dismiss / focus-traverse behaviour, which we
 * deliberately replace with the Compose-aware version above.
 *
 * @param view The EditText whose action key was triggered. Used as
 *   the anchor for `focusSearch` and `hideSoftInputFromWindow`.
 */
private fun handleImeAction(
    view: EditText,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    onImeAction()
    when (imeAction) {
        ImeAction.Next -> view.focusSearch(View.FOCUS_FORWARD)
            ?.requestFocus(View.FOCUS_FORWARD)
        ImeAction.Previous -> view.focusSearch(View.FOCUS_BACKWARD)
            ?.requestFocus(View.FOCUS_BACKWARD)
        ImeAction.None -> { /* screen callback only */ }
        else -> {
            // Done / Go / Search / Send: terminal actions — dismiss IME.
            val imm = view.context
                .getSystemService(Context.INPUT_METHOD_SERVICE)
                as? InputMethodManager
            view.clearFocus()
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}

/**
 * Translate a Compose [KeyboardType] to the bitmask of Android
 * [InputType] flags an EditText expects.
 *
 * Influences both the soft keyboard layout (numeric pad, email
 * keyboard with @ key, etc.) and what characters the EditText will
 * accept. `TYPE_TEXT_FLAG_MULTI_LINE` is OR-ed in for non-single-line
 * fields so the editor accepts newlines.
 *
 * Password fields are special-cased to ensure the bullet/dot mask
 * shows up regardless of [keyboardType] — the
 * `TYPE_TEXT_VARIATION_PASSWORD` flag is what triggers masking.
 */
private fun computeAndroidInputType(
    isPassword: Boolean,
    keyboardType: KeyboardType,
    singleLine: Boolean,
): Int {
    if (isPassword) {
        return InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    val base = when (keyboardType) {
        KeyboardType.Number, KeyboardType.NumberPassword ->
            InputType.TYPE_CLASS_NUMBER
        KeyboardType.Decimal ->
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        KeyboardType.Phone -> InputType.TYPE_CLASS_PHONE
        KeyboardType.Email ->
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        KeyboardType.Uri ->
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        KeyboardType.Password ->
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else -> InputType.TYPE_CLASS_TEXT
    }
    return if (!singleLine) base or InputType.TYPE_TEXT_FLAG_MULTI_LINE else base
}
