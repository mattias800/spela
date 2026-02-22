package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.theme.SpColor

@Composable
fun BiosWarningChip(
    missingFiles: List<BiosMissingFile>,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val fileNames = missingFiles.joinToString(", ") { it.fileName }

    SpChip(
        text = "BIOS Required",
        color = SpColor.Warning,
        isSelected = true,
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Missing BIOS files: $fileNames"
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = SpColor.Warning,
                modifier = Modifier.size(14.dp),
            )
        },
    )
}
