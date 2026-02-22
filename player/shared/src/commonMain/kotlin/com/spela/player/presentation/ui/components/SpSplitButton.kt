package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpTypography

data class SpSplitButtonMenuItem(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
fun SpSplitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SpButtonStyle = SpButtonStyle.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    menuItems: List<SpSplitButtonMenuItem> = emptyList(),
    menuContentDescription: String = "More options",
) {
    if (menuItems.isEmpty()) {
        SpButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            style = style,
            enabled = enabled,
            isLoading = isLoading,
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpButton(
            text = text,
            onClick = onClick,
            modifier = Modifier.weight(1f),
            style = style,
            enabled = enabled,
            isLoading = isLoading,
        )

        Box {
            SpButton(
                text = "",
                onClick = { expanded = true },
                style = style,
                modifier = Modifier
                    .width(48.dp)
                    .semantics { contentDescription = menuContentDescription },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                    )
                },
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                menuItems.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = item.label,
                                style = SpTypography.BodyMedium,
                            )
                        },
                        onClick = {
                            expanded = false
                            item.onClick()
                        },
                    )
                }
            }
        }
    }
}
