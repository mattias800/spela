package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import com.spela.player.presentation.ui.theme.spelaBrandGradient
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

data class SpSplitButtonMenuItem(
    val label: String,
    val description: String? = null,
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
    onGradient: Boolean = false,
) {
    if (menuItems.isEmpty()) {
        SpButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            style = style,
            enabled = enabled,
            isLoading = isLoading,
            onGradient = onGradient,
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }

    val outerShape = RoundedCornerShape(SpSpacing.RadiusLarge)
    val sharedGradient = style == SpButtonStyle.Primary && enabled

    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .then(if (sharedGradient) Modifier.background(spelaBrandGradient(), outerShape) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpButton(
            text = text,
            onClick = onClick,
            style = style,
            enabled = enabled,
            isLoading = isLoading,
            onGradient = onGradient,
            skipBackground = sharedGradient,
            shape = RoundedCornerShape(
                topStart = SpSpacing.RadiusLarge,
                bottomStart = SpSpacing.RadiusLarge,
                topEnd = 0.dp,
                bottomEnd = 0.dp,
            ),
        )

        // Divider between the two halves
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.2f)),
        )

        Box {
            SpButton(
                text = "",
                onClick = { expanded = true },
                style = style,
                enabled = enabled,
                onGradient = onGradient,
                skipBackground = sharedGradient,
                modifier = Modifier
                    .width(48.dp)
                    .semantics { contentDescription = menuContentDescription },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                    )
                },
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    bottomStart = 0.dp,
                    topEnd = SpSpacing.RadiusLarge,
                    bottomEnd = SpSpacing.RadiusLarge,
                ),
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                menuItems.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = item.label,
                                    style = SpTypography.BodyMedium,
                                )
                                if (item.description != null) {
                                    Text(
                                        text = item.description,
                                        style = SpTypography.BodySmall,
                                        color = SpColor.OnSurfaceVariant,
                                    )
                                }
                            }
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
