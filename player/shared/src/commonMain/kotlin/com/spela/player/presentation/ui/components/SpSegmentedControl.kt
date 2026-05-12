package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * DESIGN component — a view-switcher over a closed set of
 * mutually-exclusive options.
 *
 * Use when the screen has exactly N "shapes" and the user picks one
 * (group by generation vs manufacturer, list vs grid view, …). Do NOT
 * use this for picking an item from a long list — that's a dropdown.
 * Do NOT use this for multi-select filters — that's `SpChip`.
 *
 * Replaces the "two adjacent SpChips" pattern. Chips read as passive
 * metadata; on the AYN Thor under bright light they were visually
 * indistinguishable from region / status badges on the cards below
 * (#1176).
 *
 * Layout: a single connected pill track containing N equal-padding
 * segments. Selected segment uses a *filled* brand background so the
 * choice is unambiguous on bright handheld screens. Tap / A-press to
 * change. Per-segment gamepad focus is provided by [focusable]; the
 * outer Row is a [focusGroup] descendant via composition (left/right
 * DPAD inside the row naturally cycles focus across segments).
 *
 * No animation between segments — adding a thumb-slide transition is
 * a follow-up; first land the right primitive and palette.
 *
 * @param options Ordered list of `(value, label)` pairs. Equal-spaced.
 *   Provide at least 2.
 * @param selectedValue The currently-selected value. Must equal one of
 *   the option values, otherwise no segment is highlighted.
 * @param onValueChange Invoked with the new value when the user picks
 *   a different segment.
 * @param label Optional visible label rendered above the control
 *   (e.g. "Group by"). Helps first-time users.
 * @param onGradient True when this control sits on a console-coloured
 *   gradient background and needs higher-contrast surface colours.
 */
@Composable
fun <T> SpSegmentedControl(
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    onGradient: Boolean = false,
) {
    val outerShape = RoundedCornerShape(SpSpacing.RadiusPill)
    val trackBackground = if (onGradient) {
        Color.Black.copy(alpha = 0.30f)
    } else {
        SpColor.SurfaceBright.copy(alpha = 0.20f)
    }
    val trackBorder = if (onGradient) {
        Color.White.copy(alpha = 0.15f)
    } else {
        SpColor.Divider
    }

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = SpTypography.LabelSmall,
                color = if (onGradient) SpColor.OnGradientSecondary else SpColor.OnBackgroundSecondary,
                modifier = Modifier.padding(bottom = SpSpacing.XSmall),
            )
        }
        Row(
            modifier = Modifier
                .height(48.dp) // Material touch-target minimum
                .clip(outerShape)
                .background(trackBackground)
                .border(1.dp, trackBorder, outerShape)
                .semantics {
                    collectionInfo = CollectionInfo(rowCount = 1, columnCount = options.size)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val isSelected = option.value == selectedValue
                SpSegmentedControlSegment(
                    option = option,
                    isSelected = isSelected,
                    onClick = { onValueChange(option.value) },
                    onGradient = onGradient,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Stable description of one segment in [SpSegmentedControl]. Carries
 * the underlying value (an enum, string, or whatever the caller uses
 * to identify the choice) and the visible label.
 *
 * Kept as a separate type rather than `Pair<T, String>` so call sites
 * are self-documenting and the API can grow (icon, badge count, etc.)
 * without breaking existing callers.
 */
data class SegmentedOption<T>(
    val value: T,
    val label: String,
    /** Optional testTag forwarded to the segment for UI-test targeting. */
    val testTag: String? = null,
)

@Composable
private fun <T> RowScopeSegment(content: @Composable () -> Unit) = content()

@Composable
private fun SpSegmentedControlSegment(
    option: SegmentedOption<*>,
    isSelected: Boolean,
    onClick: () -> Unit,
    onGradient: Boolean,
    modifier: Modifier = Modifier,
) {
    val innerShape = RoundedCornerShape(SpSpacing.RadiusPill)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Filled brand for selected (not the 15%-alpha tint SpChip uses —
    // a primary view-switcher needs the choice to read at-a-glance,
    // especially on a sunny handheld screen).
    val background = when {
        isSelected -> SpColor.Primary
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> SpColor.OnPrimary
        onGradient -> SpColor.OnGradientPrimary
        else -> SpColor.OnBackgroundSecondary
    }
    // Focus indicator is the same white-ring vocabulary used everywhere
    // else in the player so gamepad users get a consistent cue.
    val focusBorder = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(innerShape)
            .background(background)
            .border(2.dp, focusBorder, innerShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .focusable(interactionSource = interactionSource)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
            }
            .let { mod ->
                val tag = option.testTag
                if (tag != null) mod.testTag(tag) else mod
            }
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = SpTypography.LabelMedium,
            color = textColor,
        )
    }
}
