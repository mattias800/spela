package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun BiosWarningBanner(
    consoleName: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = SpColor.Warning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "Missing BIOS files for $consoleName. Contact your server admin to upload the required firmware files."
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = SpColor.Warning,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "$consoleName requires BIOS files to play games. Ask your server admin to upload the required firmware files.",
            style = SpTypography.BodySmall,
            color = SpColor.Warning,
            modifier = Modifier.weight(1f),
        )
    }
}
