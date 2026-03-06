package com.spela.player.presentation.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.ui.theme.SpColor

@Composable
fun SpSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = SpColor.OnPrimary,
            checkedTrackColor = SpColor.Primary,
            uncheckedThumbColor = SpColor.OnBackgroundTertiary,
            uncheckedTrackColor = SpColor.SurfaceBright,
            uncheckedBorderColor = SpColor.OnBackgroundTertiary,
            disabledCheckedThumbColor = SpColor.OnBackgroundTertiary,
            disabledCheckedTrackColor = SpColor.SurfaceVariant,
            disabledUncheckedThumbColor = SpColor.SurfaceVariant,
            disabledUncheckedTrackColor = SpColor.Surface,
            disabledUncheckedBorderColor = SpColor.SurfaceVariant,
        ),
        modifier = modifier,
    )
}
