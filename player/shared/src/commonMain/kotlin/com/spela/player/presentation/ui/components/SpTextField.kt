package com.spela.player.presentation.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

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
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
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
            isError = isError || errorMessage != null,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(SpSpacing.RadiusLarge),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SpColor.OnBackground,
                unfocusedTextColor = SpColor.OnBackground,
                focusedBorderColor = SpColor.Primary,
                unfocusedBorderColor = SpColor.Divider,
                errorBorderColor = SpColor.Error,
                focusedLabelColor = SpColor.Primary,
                unfocusedLabelColor = SpColor.OnBackgroundSecondary,
                cursorColor = SpColor.Primary,
                focusedContainerColor = SpColor.SurfaceVariant,
                unfocusedContainerColor = SpColor.SurfaceVariant,
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
