package com.spela.player.presentation.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.theme.spelaBrandGradient

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
        var isFocused by remember { mutableStateOf(false) }
        val showGlow = isFocused && enabled && !isError && errorMessage == null

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
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
                .then(if (showGlow) Modifier.border(
                    1.5.dp, spelaBrandGradient(), RoundedCornerShape(SpSpacing.RadiusLarge)
                ) else Modifier),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            label = if (label.isNotEmpty()) {
                { Text(label, style = SpTypography.LabelMedium) }
            } else null,
            placeholder = if (placeholder.isNotEmpty()) {
                {
                    Text(
                        placeholder,
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            } else null,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
                // Android only: tell the IME not to go fullscreen in
                // landscape. Without this, Gboard (and many OEM keyboards)
                // occlude the whole screen with an "extract view" whenever
                // a text field has focus in a short-height window —
                // unusable on gaming handhelds like the AYN Thor, and it
                // also hides the UI from UiAutomator, which is why any
                // Android E2E test that typed into a field would appear
                // to hang at a black screen. No-op on other platforms.
                platformImeOptions = noFullscreenImeOptions(),
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            isError = isError || errorMessage != null,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(SpSpacing.RadiusLarge),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SpColor.OnBackground,
                unfocusedTextColor = SpColor.OnBackground,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                errorBorderColor = SpColor.Error,
                focusedLabelColor = SpColor.AccentPurple,
                unfocusedLabelColor = SpColor.OnBackgroundSecondary,
                cursorColor = SpColor.AccentPurple,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                disabledBorderColor = Color.White.copy(alpha = 0.06f),
                disabledTextColor = SpColor.OnBackgroundTertiary,
                disabledLabelColor = SpColor.OnBackgroundTertiary,
            ),
            textStyle = SpTypography.BodyMedium,
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
