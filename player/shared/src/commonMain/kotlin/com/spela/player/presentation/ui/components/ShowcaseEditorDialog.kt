package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.domain.model.ShowcaseAchievement
import com.spela.player.domain.model.UnlockedAchievement
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val SILVER = Color(0xFFC0C0C0)
private val GOLD = Color(0xFFFFD700)

private fun rarityColor(percent: Double): Color = when {
    percent < 1.0 -> SpColor.Primary
    percent < 5.0 -> SpColor.Primary
    percent < 20.0 -> GOLD
    percent < 50.0 -> SILVER
    else -> SpColor.OnBackgroundSecondary
}

@Composable
fun ShowcaseEditorDialog(
    selections: List<ShowcaseAchievement>,
    allUnlocked: List<UnlockedAchievement>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggle: (UnlockedAchievement) -> Unit,
    onMove: (index: Int, direction: Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedIds = selections.map { it.achievementRaId }.toSet()
    val filtered = if (searchQuery.isBlank()) {
        allUnlocked.sortedBy { it.rarityPercent }
    } else {
        val q = searchQuery.lowercase()
        allUnlocked.filter {
            it.title.lowercase().contains(q) || it.gameTitle.lowercase().contains(q)
        }.sortedBy { it.rarityPercent }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.Default)
                .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
                .background(SpColor.Surface)
                .padding(SpSpacing.Default)
                .testTag("showcase_editor_dialog"),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Edit Showcase", style = SpTypography.HeadlineSmall, color = SpColor.OnBackground)
                Text("${selections.size}/5", style = SpTypography.LabelMedium, color = SpColor.OnBackgroundSecondary)
            }

            Spacer(Modifier.height(SpSpacing.Medium))

            // Current selections with reorder buttons
            if (selections.isNotEmpty()) {
                Text("Your Showcase", style = SpTypography.LabelMedium, color = SpColor.OnBackgroundSecondary)
                Spacer(Modifier.height(SpSpacing.Small))
                selections.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                            .background(SpColor.Background)
                            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                style = SpTypography.BodyMedium,
                                color = SpColor.OnBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                entry.gameTitle,
                                style = SpTypography.LabelSmall,
                                color = SpColor.OnBackgroundSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { onMove(index, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp, "Move up",
                                tint = if (index > 0) SpColor.OnBackground else SpColor.OnBackgroundSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = { onMove(index, 1) },
                            enabled = index < selections.size - 1,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown, "Move down",
                                tint = if (index < selections.size - 1) SpColor.OnBackground else SpColor.OnBackgroundSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(SpSpacing.XSmall))
                }
                Spacer(Modifier.height(SpSpacing.Medium))
            }

            // Search
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search achievements...", color = SpColor.OnBackgroundSecondary) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SpColor.Background,
                    unfocusedContainerColor = SpColor.Background,
                    focusedTextColor = SpColor.OnBackground,
                    unfocusedTextColor = SpColor.OnBackground,
                    cursorColor = SpColor.Primary,
                    focusedIndicatorColor = SpColor.Primary,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().testTag("showcase_search"),
            )

            Spacer(Modifier.height(SpSpacing.Small))

            // Achievement list
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = SpColor.Primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    contentPadding = PaddingValues(vertical = SpSpacing.XSmall),
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    itemsIndexed(filtered, key = { _, a -> a.achievementRaId }) { _, achievement ->
                        val isSelected = achievement.achievementRaId in selectedIds
                        val canSelect = isSelected || selections.size < 5
                        AchievementPickerRow(
                            achievement = achievement,
                            isSelected = isSelected,
                            enabled = canSelect,
                            onClick = { onToggle(achievement) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(SpSpacing.Medium))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SpButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    style = SpButtonStyle.Outlined,
                )
                Spacer(Modifier.width(SpSpacing.Small))
                SpButton(
                    text = "Save",
                    onClick = onSave,
                )
            }
        }
    }
}

@Composable
private fun AchievementPickerRow(
    achievement: UnlockedAchievement,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val rColor = rarityColor(achievement.rarityPercent)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(if (isSelected) SpColor.PrimaryContainer else SpColor.Background)
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selection indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) SpColor.Primary else SpColor.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, "Selected", tint = SpColor.OnPrimary, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.width(SpSpacing.Medium))

        // Achievement info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                achievement.title,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    achievement.gameTitle,
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (achievement.rarityPercent > 0) {
                    Spacer(Modifier.width(SpSpacing.Small))
                    Text(
                        "${String.format("%.1f", achievement.rarityPercent)}%",
                        style = SpTypography.LabelSmall,
                        color = rColor,
                    )
                }
            }
        }

        // Points
        Text(
            "${achievement.points} pts",
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackgroundSecondary,
        )
    }
}
