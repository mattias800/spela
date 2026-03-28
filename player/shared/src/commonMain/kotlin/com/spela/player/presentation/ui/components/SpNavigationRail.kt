package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Layout mode for the app's main navigation, determined by available width.
 */
enum class NavigationLayoutMode {
    /** < 600dp — standard bottom tab bar (phones in portrait). */
    BOTTOM_BAR,
    /** 600–840dp — icon-only side rail (landscape phones, small tablets). */
    ICON_RAIL,
    /** > 840dp — icon + label side rail (tablets, desktop). */
    LABELED_RAIL,
}

/**
 * Side navigation rail for larger screens.
 *
 * Renders the same tabs as [SpBottomNavBar] in a vertical column.
 * Settings is pushed to the bottom, separated from the other tabs
 * by a weighted spacer.
 *
 * @param showLabels When true, shows icon + label (~200dp wide).
 *   When false, shows icon only (~72dp wide).
 */
@Composable
fun SpNavigationRail(
    activeTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val railWidth = if (showLabels) 200.dp else 72.dp

    Column(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(SpColor.SurfaceVariant)
            .padding(vertical = SpSpacing.Medium),
        horizontalAlignment = if (showLabels) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        // Main tabs (everything except Settings)
        BottomNavTab.entries.filter { it != BottomNavTab.SETTINGS }.forEach { tab ->
            RailItem(
                tab = tab,
                isSelected = tab == activeTab,
                showLabel = showLabels,
                onClick = { onTabSelected(tab) },
            )
        }

        // Push Settings to the bottom
        Spacer(Modifier.weight(1f))

        // Settings tab
        RailItem(
            tab = BottomNavTab.SETTINGS,
            isSelected = BottomNavTab.SETTINGS == activeTab,
            showLabel = showLabels,
            onClick = { onTabSelected(BottomNavTab.SETTINGS) },
        )
    }
}

@Composable
private fun RailItem(
    tab: BottomNavTab,
    isSelected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val color = when {
        isSelected -> Color.White
        isFocused -> Color.White.copy(alpha = 0.7f)
        else -> SpColor.OnBackgroundSecondary
    }

    Box(
        modifier = Modifier
            .then(
                if (showLabel) Modifier.padding(horizontal = SpSpacing.Small) else Modifier
            )
            .height(56.dp)
            .then(
                if (showLabel) Modifier.width(184.dp) else Modifier.width(56.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) Color.Black.copy(alpha = 0.3f) else Color.Transparent,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .focusable(interactionSource = interactionSource)
            .semantics {
                contentDescription = tab.label
                role = Role.Tab
            },
        contentAlignment = if (showLabel) Alignment.CenterStart else Alignment.Center,
    ) {
        if (showLabel) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = SpSpacing.Medium),
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(SpSpacing.Medium))
                Text(
                    text = tab.label,
                    style = SpTypography.LabelMedium,
                    color = color,
                )
            }
        } else {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
