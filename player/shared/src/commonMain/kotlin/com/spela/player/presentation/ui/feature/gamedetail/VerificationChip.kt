package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.theme.SpColor

@Composable
fun VerificationChip(
    verificationStatus: String?,
    verificationTag: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when (verificationStatus) {
        "verified" -> SpChip(
            text = "Verified",
            color = SpColor.Success,
            isSelected = true,
            modifier = modifier.semantics {
                contentDescription = "ROM verification: Verified"
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = SpColor.Success,
                    modifier = Modifier.size(14.dp),
                )
            },
        )
        "unverified" -> {
            val label = verificationTag?.takeIf { it.isNotBlank() } ?: "Unverified"
            SpChip(
                text = label,
                color = SpColor.Warning,
                isSelected = true,
                onClick = onClick,
                modifier = modifier.semantics {
                    contentDescription = "ROM verification: $label"
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
    }
}
