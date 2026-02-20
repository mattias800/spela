package com.spela.player.presentation.ui.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = SpTypography.HeadlineSmall,
        color = SpColor.OnBackground,
        modifier = Modifier
            .padding(top = SpSpacing.Small)
            .semantics { heading() },
    )
}

/**
 * Groups a section header with its content, adding consistent spacing
 * between groups and between the header and its content.
 */
@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        SettingsSectionHeader(title = title)
        Spacer(Modifier.height(SpSpacing.Medium))
        content()
    }
}

@Composable
internal fun SettingsToggle(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .spFocusRing(shape = RoundedCornerShape(SpSpacing.RadiusLarge))
            .clickable(onClick = onToggle)
            .focusable()
            .semantics {
                contentDescription = title
                role = Role.Switch
                stateDescription = if (isChecked) "On" else "Off"
            }
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SpTypography.TitleMedium,
                color = SpColor.OnCard,
            )
            Text(
                text = subtitle,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
        Spacer(Modifier.width(SpSpacing.Medium))
        Switch(
            checked = isChecked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SpColor.OnPrimary,
                checkedTrackColor = SpColor.Primary,
                uncheckedThumbColor = SpColor.OnBackgroundTertiary,
                uncheckedTrackColor = SpColor.SurfaceBright,
            ),
        )
    }
}

@Composable
internal fun SettingsDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = SpSpacing.Default)
            .background(SpColor.Divider),
    )
}

@Composable
internal fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
        Text(
            text = value,
            style = SpTypography.BodyMedium,
            color = SpColor.OnCard,
        )
    }
}
