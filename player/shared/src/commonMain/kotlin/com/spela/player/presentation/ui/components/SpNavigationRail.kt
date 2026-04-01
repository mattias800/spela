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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
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

    val titleBarInset = LocalTitleBarInset.current

    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(SpColor.Surface)
                .padding(top = if (titleBarInset > 0.dp) titleBarInset else SpSpacing.Medium, bottom = SpSpacing.Medium),
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

        // Right edge highlight
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            SpColor.GradientStart.copy(alpha = 0.3f),
                            SpColor.GradientMid.copy(alpha = 0.3f),
                            SpColor.GradientEnd.copy(alpha = 0.3f),
                        ),
                    ),
                ),
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
        isFocused -> SpColor.AccentPurple
        else -> SpColor.OnBackgroundTertiary
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
                if (isFocused) Color.White.copy(alpha = 0.05f) else Color.Transparent,
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
                // Active indicator — gradient bar on the left
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        SpColor.GradientStart,
                                        SpColor.GradientMid,
                                        SpColor.GradientEnd,
                                    ),
                                ),
                            ),
                    )
                    Spacer(Modifier.width(SpSpacing.Medium))
                }
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                // Active indicator — gradient dot below icon
                if (isSelected) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        SpColor.GradientStart,
                                        SpColor.GradientMid,
                                        SpColor.GradientEnd,
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
    }
}
