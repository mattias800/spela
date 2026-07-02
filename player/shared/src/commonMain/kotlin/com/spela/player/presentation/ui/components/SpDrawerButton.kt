package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpDrawerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String = text,
    stateDescription: String? = null,
    shape: Shape = RoundedCornerShape(SpSpacing.RadiusMedium),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor = when {
        !enabled -> SpColor.OnDrawerTertiary
        selected -> SpColor.PrimaryDark
        else -> SpColor.OnDrawer
    }
    val iconColor = when {
        !enabled -> SpColor.OnDrawerTertiary
        selected -> SpColor.PrimaryDark
        else -> SpColor.OnDrawerSecondary
    }
    val containerColor = when {
        selected -> SpColor.DrawerControlSelected
        isPressed -> SpColor.DrawerControlPressed
        isHovered -> SpColor.DrawerControl
        else -> Color.Transparent
    }
    val focusMods = Modifier
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && enabled) {
                when (event.key) {
                    Key.Enter, Key.Spacebar, Key.DirectionCenter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        .gamepadFocusable(
            shape = shape,
            interactionSource = interactionSource,
            focusRingColor = SpColor.PrimaryDark,
            addFocusable = false,
        )

    Row(
        modifier = modifier
            .heightIn(min = SpSpacing.XXLarge + SpSpacing.Small)
            .clip(shape)
            .background(containerColor, shape)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                stateDescription?.let { this.stateDescription = it }
                if (selected) this.selected = true
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(focusMods),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = SpSpacing.Medium)
                .width(SpSpacing.XXLarge),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(SpSpacing.IconDefault),
                )
            }
        }
        Text(
            text = text,
            style = SpTypography.LabelLarge,
            color = contentColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = SpSpacing.Medium),
        )
    }
}

@Composable
fun SpDrawerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltip: String = contentDescription,
    stateDescription: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(SpSpacing.RadiusMedium),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val tooltipOffsetY = with(LocalDensity.current) {
        (SpSpacing.XXLarge + SpSpacing.Medium).roundToPx()
    }
    val iconColor = when {
        !enabled -> SpColor.OnDrawerTertiary
        selected -> SpColor.PrimaryDark
        else -> SpColor.OnDrawerSecondary
    }
    val containerColor = when {
        selected -> SpColor.DrawerControlSelected
        isPressed -> SpColor.DrawerControlPressed
        isHovered || isFocused -> SpColor.DrawerControl
        else -> Color.Transparent
    }
    val focusMods = Modifier
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && enabled) {
                when (event.key) {
                    Key.Enter, Key.Spacebar, Key.DirectionCenter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
        .gamepadFocusable(
            shape = shape,
            interactionSource = interactionSource,
            focusRingColor = SpColor.PrimaryDark,
            addFocusable = false,
        )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(SpSpacing.XXLarge + SpSpacing.Small)
                .clip(shape)
                .background(containerColor, shape)
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription?.let { this.stateDescription = it }
                    role = Role.Button
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .then(focusMods),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(SpSpacing.IconDefault),
            )
        }

        if (tooltip.isNotBlank() && (isHovered || isFocused)) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = tooltipOffsetY),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                        .background(SpColor.OnDrawer)
                        .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall),
                ) {
                    Text(
                        text = tooltip,
                        style = SpTypography.LabelMedium,
                        color = SpColor.DrawerSurface,
                    )
                }
            }
        }
    }
}
