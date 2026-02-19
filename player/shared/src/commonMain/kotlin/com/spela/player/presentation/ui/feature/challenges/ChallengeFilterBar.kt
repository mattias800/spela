package com.spela.player.presentation.ui.feature.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.Console
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private data class FilterOption(val value: String?, val label: String)

@Composable
internal fun ChallengeFilterBar(
    consoles: List<Console>,
    selectedConsoleFilter: String?,
    selectedDifficultyFilter: String?,
    onFilterByConsole: (String?) -> Unit,
    onFilterByDifficulty: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        val consoleOptions = listOf(FilterOption(null, "All Consoles")) +
            consoles.map { FilterOption(it.id, it.name) }

        val difficultyOptions = listOf(
            FilterOption(null, "All Difficulties"),
            FilterOption("easy", "Easy"),
            FilterOption("medium", "Medium"),
            FilterOption("hard", "Hard"),
        )

        FilterDropdown(
            options = consoleOptions,
            selectedValue = selectedConsoleFilter,
            onSelected = onFilterByConsole,
            label = "Console",
            modifier = Modifier.weight(1f),
        )

        FilterDropdown(
            options = difficultyOptions,
            selectedValue = selectedDifficultyFilter,
            onSelected = onFilterByDifficulty,
            label = "Difficulty",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterDropdown(
    options: List<FilterOption>,
    selectedValue: String?,
    onSelected: (String?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: options.first().label

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                .background(SpColor.SurfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
                .semantics {
                    contentDescription = "$label: $currentLabel"
                    role = Role.DropdownList
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = null,
                tint = SpColor.OnBackgroundSecondary,
                modifier = Modifier.size(SpSpacing.IconSmall),
            )
            Spacer(Modifier.width(SpSpacing.Small))
            Text(
                text = currentLabel,
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundSecondary,
                maxLines = 1,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = SpTypography.BodyMedium,
                            color = if (option.value == selectedValue) SpColor.Primary else SpColor.OnBackground,
                        )
                    },
                    onClick = {
                        onSelected(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}
