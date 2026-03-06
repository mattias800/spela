package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.domain.model.Cheat
import com.spela.player.presentation.ui.components.SpSwitch
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun CheatToggleRow(
    cheat: Cheat,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SpSpacing.Small)
            .semantics { contentDescription = "Cheat: ${cheat.description}" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = cheat.description,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackground,
            modifier = Modifier.weight(1f).padding(end = SpSpacing.Small),
        )
        SpSwitch(
            checked = cheat.enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
fun CheatsDialog(
    cheats: List<Cheat>,
    onToggle: (cheatId: String, enabled: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge),
        ) {
            Text(
                text = "Cheats",
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )

            Spacer(Modifier.height(SpSpacing.Default))

            if (cheats.isEmpty()) {
                Text(
                    text = "No cheats available for this game.",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                )
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    cheats.forEach { cheat ->
                        CheatToggleRow(
                            cheat = cheat,
                            onToggle = { enabled -> onToggle(cheat.id, enabled) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(SpSpacing.XLarge))

            SpButton(
                text = "Done",
                onClick = onDismiss,
                style = SpButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
