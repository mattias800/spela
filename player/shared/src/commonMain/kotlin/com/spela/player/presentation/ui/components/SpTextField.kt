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

        // The actual text-input widget is platform-specific so Android
        // can fall back to AndroidView+EditText with the right
        // IME_FLAG_NO_EXTRACT_UI / IME_FLAG_NO_FULLSCREEN flags set
        // on EditorInfo (Compose Multiplatform doesn't expose those
        // flags through KeyboardOptions). See PlatformTextFieldCore.
        PlatformTextFieldCore(
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
            isPassword = isPassword,
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
