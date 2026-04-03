package com.spela.player.presentation.ui.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun RecentSearchesSection(
    recentSearches: List<String>,
    onRecentSearchSelected: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recent_searches_section"),
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SpSpacing.ScreenHorizontal,
                    vertical = SpSpacing.Small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent searches",
                style = SpTypography.TitleMedium,
                color = SpColor.OnBackground,
                modifier = Modifier.weight(1f),
            )
            SpButton(
                text = "Clear all",
                onClick = onClearAll,
                style = SpButtonStyle.Ghost,
                modifier = Modifier.testTag("recent_searches_clear_all"),
            )
        }

        // Recent search items
        recentSearches.forEach { query ->
            RecentSearchItem(
                query = query,
                onClick = { onRecentSearchSelected(query) },
                onRemove = { onRemoveRecentSearch(query) },
            )
        }

        Spacer(Modifier.height(SpSpacing.Medium))
    }
}

@Composable
private fun RecentSearchItem(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
            .clickable(onClick = onClick)
            .gamepadFocusable(shape = RoundedCornerShape(SpSpacing.RadiusDefault))
            .padding(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Small,
            )
            .testTag("recent_search_item_$query")
            .semantics {
                contentDescription = "Recent search: $query"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = SpColor.OnBackgroundTertiary,
            modifier = Modifier.size(SpSpacing.IconDefault),
        )
        Text(
            text = query,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(SpSpacing.IconXLarge)
                .testTag("recent_search_remove_$query"),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove $query",
                tint = SpColor.OnBackgroundTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
