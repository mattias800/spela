package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val SLOT_CARD_WIDTH = 80.dp
private val SLOT_CARD_HEIGHT = 96.dp
private val ACTIVE_BORDER_WIDTH = 2.dp
private val SLOT_RANGE = 1..10

/**
 * Save Slots page for the secondary screen companion.
 *
 * Shows a horizontal scrollable row of save slot cards (1-10).
 * The active slot is highlighted with a [SpColor.Primary] border.
 * Tapping a slot card selects it as the active quick-save/load target.
 */
@Composable
fun SecondarySaveSlotsPage(
    activeSlot: Int,
    onSelectSlot: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SpSpacing.Medium),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Title
        Text(
            text = "Save Slots",
            style = SpTypography.HeadlineSmall,
            color = SpColor.OnBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.Medium)
                .padding(bottom = SpSpacing.Medium)
                .semantics { contentDescription = "Save Slots" },
        )

        // Horizontal scrollable row of slot cards
        LazyRow(
            contentPadding = PaddingValues(horizontal = SpSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            items(SLOT_RANGE.toList()) { slot ->
                val isActive = slot == activeSlot
                SaveSlotCard(
                    slot = slot,
                    isActive = isActive,
                    onClick = { onSelectSlot(slot) },
                )
            }
        }

        // Hint text
        Text(
            text = "Tap to select active slot",
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
            modifier = Modifier
                .padding(top = SpSpacing.Medium)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SaveSlotCard(
    slot: Int,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)
    val borderModifier = if (isActive) {
        Modifier.border(ACTIVE_BORDER_WIDTH, SpColor.Primary, shape)
    } else {
        Modifier.border(ACTIVE_BORDER_WIDTH, SpColor.SurfaceVariant, shape)
    }
    val semanticDesc = if (isActive) "Save slot $slot, active" else "Save slot $slot"

    Column(
        modifier = Modifier
            .width(SLOT_CARD_WIDTH)
            .height(SLOT_CARD_HEIGHT)
            .clip(shape)
            .then(borderModifier)
            .background(SpColor.Card, shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = semanticDesc }
            .padding(SpSpacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Slot number
        Text(
            text = "$slot",
            style = SpTypography.HeadlineSmall,
            color = if (isActive) SpColor.Primary else SpColor.OnBackground,
            textAlign = TextAlign.Center,
        )

        // Label
        Text(
            text = if (isActive) "Active" else "Slot",
            style = SpTypography.LabelSmall,
            color = if (isActive) SpColor.Primary else SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
