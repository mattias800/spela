package com.spela.player.presentation.ui.components.keymapping

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ConsoleButtonLayout
import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * A scrollable list showing each button's current key mapping.
 * Each row is clickable to enter listening mode for that button.
 * The currently highlighted button row pulses with the accent color.
 *
 * @param layout Console button layout defining which buttons to show
 * @param currentBindings Map of retroButtonId to platform key code
 * @param highlightedButton The retroButtonId currently being mapped (or null)
 * @param onButtonClick Called when a row is tapped — enters listening mode for that button
 * @param keyNameResolver Converts a platform key code to a human-readable name
 * @param modifier Standard modifier
 */
@Composable
fun MappingListPanel(
    layout: ConsoleButtonLayout,
    currentBindings: Map<Int, Int>,
    highlightedButton: Int?,
    onButtonClick: (Int) -> Unit,
    keyNameResolver: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val pulseTransition = rememberInfiniteTransition(label = "listPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "listPulseAlpha",
    )

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(layout.buttons, key = { it.retroButtonId }) { button ->
            val isHighlighted = button.retroButtonId == highlightedButton
            val keyCode = currentBindings[button.retroButtonId]
            val isMapped = keyCode != null
            val mappedName = keyCode?.let { keyNameResolver(it) }

            val bgColor by animateColorAsState(
                targetValue = when {
                    isHighlighted -> SpColor.Primary.copy(alpha = pulseAlpha)
                    else -> Color.Transparent
                },
                animationSpec = tween(150),
                label = "mappingRowBg",
            )

            val rowDescription = if (isHighlighted) {
                "${button.label}, press a key to map"
            } else if (isMapped) {
                "${button.label} mapped to $mappedName"
            } else {
                "${button.label}, not mapped"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                    .background(bgColor)
                    .clickable { onButtonClick(button.retroButtonId) }
                    .focusable()
                    .semantics {
                        contentDescription = rowDescription
                        role = Role.Button
                    }
                    .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Button label
                Text(
                    text = button.label,
                    style = SpTypography.TitleSmall,
                    color = if (isHighlighted) SpColor.Primary else SpColor.OnSurface,
                    modifier = Modifier.weight(1f),
                )

                // Arrow
                Text(
                    text = "\u2192",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.padding(horizontal = SpSpacing.Small),
                )

                // Mapped key or status
                Text(
                    text = when {
                        isHighlighted -> "Press a key\u2026"
                        isMapped -> mappedName!!
                        else -> "Not mapped"
                    },
                    style = if (isHighlighted) SpTypography.TitleSmall else SpTypography.BodyMedium,
                    color = when {
                        isHighlighted -> SpColor.Primary
                        isMapped -> SpColor.OnSurface
                        else -> SpColor.OnBackgroundTertiary
                    },
                )
            }
        }
    }
}

/**
 * Read-only condensed version of the mapping list for use in the wizard.
 * Shows mapping progress without click handlers.
 */
@Composable
fun MappingListCondensed(
    layout: ConsoleButtonLayout,
    currentBindings: Map<Int, Int>,
    highlightedButton: Int?,
    keyNameResolver: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(layout.buttons, key = { it.retroButtonId }) { button ->
            val isHighlighted = button.retroButtonId == highlightedButton
            val keyCode = currentBindings[button.retroButtonId]
            val isMapped = keyCode != null

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.Small, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = button.label,
                    style = SpTypography.LabelSmall,
                    color = if (isHighlighted) SpColor.Primary else SpColor.OnBackgroundSecondary,
                )
                Text(
                    text = when {
                        isHighlighted -> "\u25B6"
                        isMapped -> keyNameResolver(keyCode)
                        else -> "\u2014"
                    },
                    style = SpTypography.LabelSmall,
                    color = when {
                        isHighlighted -> SpColor.Primary
                        isMapped -> SpColor.OnSurface
                        else -> SpColor.OnBackgroundTertiary
                    },
                )
            }
        }
    }
}
