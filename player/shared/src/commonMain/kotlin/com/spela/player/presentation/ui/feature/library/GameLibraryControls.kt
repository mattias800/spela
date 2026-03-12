package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
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
import com.spela.player.presentation.state.ViewMode
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private data class SortOption(val value: String, val label: String)

private val sortOptions = listOf(
    SortOption("title", "Title"),
    SortOption("created_at", "Recently Added"),
    SortOption("file_size", "File Size"),
    SortOption("rating", "Rating"),
)

@Composable
internal fun GameLibraryControls(
    consoles: List<Console>,
    selectedConsoleFilter: String?,
    sortBy: String,
    sortOrder: String,
    viewMode: ViewMode,
    hideBetas: Boolean = true,
    onFilterByConsole: (String?) -> Unit,
    onSortByChanged: (String) -> Unit,
    onSortOrderChanged: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onToggleHideBetas: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        // Console filter chips (horizontally scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = SpSpacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            FilterChip(
                text = "All Consoles",
                isSelected = selectedConsoleFilter == null,
                onClick = { onFilterByConsole(null) },
            )
            consoles.forEach { console ->
                FilterChip(
                    text = console.name,
                    isSelected = selectedConsoleFilter == console.id,
                    onClick = { onFilterByConsole(console.id) },
                )
            }
        }

        // Sort controls + hide betas + view mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.ScreenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            SortDropdown(
                sortBy = sortBy,
                onSortByChanged = onSortByChanged,
                modifier = Modifier.weight(1f),
            )

            SortDirectionButton(
                sortOrder = sortOrder,
                onToggle = {
                    onSortOrderChanged(if (sortOrder == "asc") "desc" else "asc")
                },
            )

            FilterChip(
                text = "Hide betas",
                isSelected = hideBetas,
                onClick = onToggleHideBetas,
            )

            ViewModeToggle(
                viewMode = viewMode,
                onToggle = onToggleViewMode,
            )
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.RadiusPill)
    val backgroundColor = if (isSelected) SpColor.Primary.copy(alpha = 0.2f) else SpColor.SurfaceVariant
    val textColor = if (isSelected) SpColor.Primary else SpColor.OnBackgroundSecondary

    Box(
        modifier = Modifier
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "$text filter${if (isSelected) ", selected" else ""}"
                role = Role.Tab
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = SpTypography.LabelMedium,
            color = textColor,
        )
    }
}

@Composable
private fun SortDropdown(
    sortBy: String,
    onSortByChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = sortOptions.firstOrNull { it.value == sortBy }?.label ?: "Title"

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                .background(SpColor.SurfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
                .semantics {
                    contentDescription = "Sort by $currentLabel"
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
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sortOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = SpTypography.BodyMedium,
                            color = if (option.value == sortBy) SpColor.Primary else SpColor.OnBackground,
                        )
                    },
                    onClick = {
                        onSortByChanged(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SortDirectionButton(
    sortOrder: String,
    onToggle: () -> Unit,
) {
    val icon = if (sortOrder == "asc") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    val label = if (sortOrder == "asc") "Sort ascending" else "Sort descending"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(SpColor.SurfaceVariant)
            .clickable(onClick = onToggle)
            .padding(SpSpacing.Small)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpColor.OnBackgroundSecondary,
            modifier = Modifier.size(SpSpacing.IconDefault),
        )
    }
}

@Composable
private fun ViewModeToggle(
    viewMode: ViewMode,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(SpColor.SurfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    if (viewMode == ViewMode.GRID) SpColor.Primary else SpColor.SurfaceVariant,
                )
                .clickable(onClick = { if (viewMode != ViewMode.GRID) onToggle() })
                .padding(SpSpacing.Small)
                .semantics {
                    contentDescription = "Grid view${if (viewMode == ViewMode.GRID) ", selected" else ""}"
                    role = Role.Tab
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.GridView,
                contentDescription = null,
                tint = if (viewMode == ViewMode.GRID) SpColor.OnPrimary else SpColor.OnBackgroundTertiary,
                modifier = Modifier.size(SpSpacing.IconDefault),
            )
        }

        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    if (viewMode == ViewMode.LIST) SpColor.Primary else SpColor.SurfaceVariant,
                )
                .clickable(onClick = { if (viewMode != ViewMode.LIST) onToggle() })
                .padding(SpSpacing.Small)
                .semantics {
                    contentDescription = "List view${if (viewMode == ViewMode.LIST) ", selected" else ""}"
                    role = Role.Tab
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = if (viewMode == ViewMode.LIST) SpColor.OnPrimary else SpColor.OnBackgroundTertiary,
                modifier = Modifier.size(SpSpacing.IconDefault),
            )
        }
    }
}
