package com.spela.player.presentation.ui.components

import android.graphics.drawable.GradientDrawable
import android.graphics.Color as AndroidColor
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.theme.spelaBrandGradient

/**
 * Android implementation of [PlatformTextFieldCore].
 *
 * Wraps a standard `EditText` via `AndroidView` so we can set
 * `EditorInfo.IME_FLAG_NO_EXTRACT_UI | IME_FLAG_NO_FULLSCREEN` on
 * its `imeOptions`. Compose Multiplatform 1.10's `KeyboardOptions`
 * exposes only `privateImeOptions` (a free-form string hint that
 * Gboard ignores), so dropping to the view system is the only
 * reliable way to keep the IME from going fullscreen on landscape
 * Android handhelds (AYN Thor and similar). See
 * `PlatformTextFieldCore.kt` in commonMain for the rationale.
 *
 * Visual goal: match `OutlinedTextField`'s appearance closely
 * enough that the existing screens look right. Not pixel-perfect.
 * The wrapper in `SpTextField` already supplies the focus glow
 * and rounded outline; we just render the inner editor.
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
    val borderColor = when {
        isError -> SpColor.Error
        else -> Color.White.copy(alpha = 0.15f)
    }
    val textColorArgb = if (enabled) SpColor.OnBackground.toArgb()
    else SpColor.OnBackgroundTertiary.toArgb()
    val hintColorArgb = SpColor.OnBackgroundTertiary.toArgb()
    val cursorColorArgb = SpColor.AccentPurple.toArgb()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
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
                factory = { ctx ->
                    EditText(ctx).apply {
                        // Background outline + transparent fill — Compose
                        // wrapper draws the focus glow / error tint, so a
                        // simple thin border is enough here.
                        background = GradientDrawable().apply {
                            cornerRadius = SpSpacing.RadiusLarge.value * resources.displayMetrics.density
                            setStroke(
                                (1 * resources.displayMetrics.density).toInt(),
                                borderColor.toArgb(),
                            )
                            setColor(AndroidColor.TRANSPARENT)
                        }
                        setTextColor(textColorArgb)
                        setHintTextColor(hintColorArgb)
                        textSize = 16f
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        // Single-line vs multi-line input. Disambiguate
                        // the function-parameter `minLines` / `maxLines`
                        // from the EditText properties via `setMinLines` /
                        // `setMaxLines`.
                        isSingleLine = singleLine
                        if (singleLine) {
                            setMaxLines(1)
                        } else {
                            setMinLines(minLines)
                            setMaxLines(maxLines)
                        }
                        // Input type — password / email / numeric / text.
                        // Re-evaluated on every recomposition via the
                        // update block below so toggling isPassword
                        // mid-life works.
                        inputType = computeAndroidInputType(
                            isPassword,
                            keyboardType,
                            singleLine,
                        )
                        if (isPassword) {
                            transformationMethod = PasswordTransformationMethod.getInstance()
                        }
                        // The actual fix: set the standard Android IME
                        // flags so Gboard / OEM keyboards skip
                        // fullscreen extract mode in landscape. This is
                        // why we're wrapping a plain EditText instead
                        // of using Compose's TextField.
                        imeOptions = imeActionToEditorInfo(imeAction) or
                            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                            EditorInfo.IME_FLAG_NO_FULLSCREEN
                        // Padding inside the field (left, top, right, bottom)
                        val pad = (SpSpacing.Default.value * resources.displayMetrics.density).toInt()
                        setPadding(pad, pad / 2, pad, pad / 2)
                        // Reflect the cursor accent.
                        // textCursorDrawable requires API 29+, fallback
                        // is the system default cursor.
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            textCursorDrawable = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                setSize((2 * resources.displayMetrics.density).toInt(), 0)
                                setColor(cursorColorArgb)
                            }
                        }
                        // Hint text (placeholder).
                        hint = placeholder.ifEmpty { label }
                        // Forward IME action to the Compose callback.
                        setOnEditorActionListener { _, actionId, _ ->
                            // Honour the action that matches our imeAction
                            // mapping; ignore others (autofill / etc.).
                            if (actionId == imeActionToEditorInfo(imeAction) ||
                                actionId == EditorInfo.IME_ACTION_DONE) {
                                onImeAction()
                                true
                            } else false
                        }
                        // Wire change events back to Compose state.
                        addTextChangedListener(SimpleTextWatcher { newText ->
                            if (newText != value) onValueChange(newText)
                        })
                    }
                },
                update = { editText ->
                    // Sync state back from Compose. Guard with a
                    // non-equality check so Android doesn't bounce the
                    // cursor / IME composition on every recomposition.
                    if (editText.text.toString() != value) {
                        val cursor = editText.selectionStart
                        editText.setText(value)
                        editText.setSelection(cursor.coerceIn(0, value.length))
                    }
                    editText.isEnabled = enabled
                    editText.hint = placeholder.ifEmpty { label }
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
                    val targetBorder = (if (isError) SpColor.Error
                    else Color.White.copy(alpha = 0.15f)).toArgb()
                    val bg = editText.background
                    if (bg is GradientDrawable) {
                        bg.setStroke(
                            (1 * editText.resources.displayMetrics.density).toInt(),
                            targetBorder,
                        )
                    }
                },
            )
        }

        if (trailingIcon != null) {
            Box(modifier = Modifier.padding(start = SpSpacing.Small)) { trailingIcon() }
        }
    }
}

private class SimpleTextWatcher(
    private val onChange: (String) -> Unit,
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) {
        onChange(s?.toString().orEmpty())
    }
}

/** Map Compose [ImeAction] to Android's [EditorInfo] action constant. */
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

/** Map Compose [KeyboardType] to Android [InputType] flags. */
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
