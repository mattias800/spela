package com.spela.player.presentation.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
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
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        isError = isError,
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
}
