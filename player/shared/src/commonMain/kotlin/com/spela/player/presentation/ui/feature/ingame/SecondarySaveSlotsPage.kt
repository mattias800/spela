package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.state.SaveSlotInfo
import androidx.compose.ui.focus.focusRequester
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpImage
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val SLOT_CARD_WIDTH = 80.dp
private val SLOT_CARD_HEIGHT = 96.dp
private val ACTIVE_BORDER_WIDTH = 2.dp
private val SLOT_RANGE = 1..10
private const val SWIPE_UP_THRESHOLD = -80f
private const val FLASH_DURATION_MS = 400
private val FLASH_COLOR_SAVE = SpColor.Success
private val FLASH_COLOR_LOAD = SpColor.Accent

/**
 * Save Slots page for the secondary screen companion.
 *
 * Shows a horizontal scrollable row of save slot cards (1-10).
 * The active slot is highlighted with a [SpColor.Primary] border.
 * Tapping a slot card selects it as the active quick-save/load target.
 *
 * Gestures:
 * - Swipe up on any slot to save to it.
 * - Long-press on a filled slot to load from it.
 * - Long-press on an empty slot to save to it.
 */
@Composable
fun SecondarySaveSlotsPage(
    activeSlot: Int,
    saveSlots: Map<Int, SaveSlotInfo>,
    onSelectSlot: (Int) -> Unit,
    onSaveToSlot: (Int) -> Unit,
    onLoadFromSlot: (Int) -> Unit,
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
        val slots = SLOT_RANGE.toList()
        SpCarousel(itemCount = slots.size) { index, focusRequester ->
            val slot = slots[index]
            val isActive = slot == activeSlot
            val slotInfo = saveSlots[slot]
            Box(modifier = Modifier.focusRequester(focusRequester)) {
                SaveSlotCard(
                    slot = slot,
                    isActive = isActive,
                    slotInfo = slotInfo,
                    onClick = { onSelectSlot(slot) },
                    onSwipeUp = { onSaveToSlot(slot) },
                    onLongPress = {
                        if (slotInfo?.isFilled == true) {
                            onLoadFromSlot(slot)
                        } else {
                            onSaveToSlot(slot)
                        }
                    },
                )
            }
        }

        // Hint text
        Text(
            text = "Swipe up: save \u2022 Hold: load",
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
            modifier = Modifier
                .padding(top = SpSpacing.Medium)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaveSlotCard(
    slot: Int,
    isActive: Boolean,
    slotInfo: SaveSlotInfo?,
    onClick: () -> Unit,
    onSwipeUp: () -> Unit,
    onLongPress: () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)
    val borderModifier = if (isActive) {
        Modifier.border(ACTIVE_BORDER_WIDTH, SpColor.Primary, shape)
    } else {
        Modifier.border(ACTIVE_BORDER_WIDTH, SpColor.SurfaceVariant, shape)
    }

    val isFilled = slotInfo?.isFilled == true
    val semanticDesc = buildString {
        append("Save slot $slot")
        if (isActive) append(", active")
        if (isFilled) append(", filled")
        if (slotInfo?.timestamp != null) append(", saved at ${slotInfo.timestamp}")
    }

    // Flash overlay animation: increment key to trigger a flash
    var flashKey by remember { mutableIntStateOf(0) }
    var flashColor by remember { mutableStateOf(Color.Transparent) }
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(flashKey) {
        if (flashKey > 0) {
            flashAlpha.snapTo(0.5f)
            flashAlpha.animateTo(0f, animationSpec = tween(FLASH_DURATION_MS))
        }
    }

    Box(
        modifier = Modifier
            .width(SLOT_CARD_WIDTH)
            .height(SLOT_CARD_HEIGHT)
            .clip(shape)
            .then(borderModifier)
            .background(SpColor.Card, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    flashColor = if (isFilled) FLASH_COLOR_LOAD else FLASH_COLOR_SAVE
                    flashKey++
                    onLongPress()
                },
            )
            .pointerInput(Unit) {
                var totalDrag = 0f
                var gestureHandled = false
                detectVerticalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        gestureHandled = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                        if (!gestureHandled && totalDrag < SWIPE_UP_THRESHOLD) {
                            gestureHandled = true
                            flashColor = FLASH_COLOR_SAVE
                            flashKey++
                            onSwipeUp()
                        }
                    },
                )
            }
            .semantics { contentDescription = semanticDesc },
    ) {
        // Thumbnail or placeholder content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(SpSpacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isFilled && slotInfo.screenshotUrl != null) {
                // Thumbnail image
                SpImage(
                    model = slotInfo.screenshotUrl,
                    contentDescription = "Save slot $slot screenshot",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(SpSpacing.CardCornerRadius / 2)),
                    contentScale = ContentScale.Crop,
                    placeholder = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(SpColor.SurfaceBright.copy(alpha = 0.25f)),
                        )
                    },
                    error = {
                        SlotPlaceholderContent(slot = slot, isActive = isActive, isFilled = true)
                    },
                )
                // Timestamp below thumbnail
                if (slotInfo.timestamp != null) {
                    Text(
                        text = slotInfo.timestamp,
                        style = SpTypography.LabelSmall,
                        color = if (isActive) SpColor.Primary else SpColor.OnBackgroundTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = SpSpacing.XXSmall),
                    )
                }
            } else {
                SlotPlaceholderContent(slot = slot, isActive = isActive, isFilled = isFilled)
            }
        }

        // Flash overlay for save/load visual feedback
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(flashColor.copy(alpha = flashAlpha.value), shape),
            )
        }
    }
}

/**
 * Placeholder content shown when a slot has no screenshot thumbnail.
 */
@Composable
private fun SlotPlaceholderContent(
    slot: Int,
    isActive: Boolean,
    isFilled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Slot number or plus icon
        Text(
            text = if (isFilled) "$slot" else "+",
            style = SpTypography.HeadlineSmall,
            color = when {
                isActive -> SpColor.Primary
                isFilled -> SpColor.OnBackground
                else -> SpColor.OnBackgroundTertiary
            },
            textAlign = TextAlign.Center,
        )

        // Label
        Text(
            text = when {
                isActive -> "Active"
                isFilled -> "Slot $slot"
                else -> "Empty"
            },
            style = SpTypography.LabelSmall,
            color = if (isActive) SpColor.Primary else SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
