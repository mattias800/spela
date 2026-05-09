package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * List of settings categories for the left panel (or full-screen on phones).
 */
@Composable
fun SettingsCategoryList(
    selectedCategory: SettingsCategory?,
    onSelectCategory: (SettingsCategory) -> Unit,
    username: String,
    serverUrl: String,
    modifier: Modifier = Modifier,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val focusMemory = rememberFocusMemoryState()
    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
    LazyColumn(
        modifier = modifier.fillMaxSize().focusGroup(),
        contentPadding = PaddingValues(
            start = SpSpacing.Medium,
            end = SpSpacing.Medium,
            top = topPadding + SpSpacing.Default,
            bottom = SpSpacing.Default,
        ),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
    ) {
        // Account header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Medium),
            ) {
                if (username.isNotEmpty()) {
                    Text(
                        text = username,
                        style = SpTypography.TitleLarge,
                        color = SpColor.OnBackground,
                    )
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = serverUrl.ifEmpty { "Connected" },
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(SpSpacing.Small)) }

        // Category items
        val categories = SettingsCategory.entries.toList()
        items(categories) { category ->
            val isSelected = category == selectedCategory
            val bgColor = if (isSelected) SpColor.Primary.copy(alpha = 0.15f) else Color.Transparent
            val textColor = if (isSelected) SpColor.PrimaryLight else SpColor.OnBackground

            Row(
                modifier = Modifier
                    .testTag(category.testTag)
                    .focusRestoreItem(
                        key = "settings_category_${category.name}",
                        isDefault = category == categories.firstOrNull(),
                    )
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onSelectCategory(category) }
                    .gamepadFocusable(shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Medium)
                    .semantics { contentDescription = category.label },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(SpSpacing.Medium))
                Text(
                    text = category.label,
                    style = SpTypography.TitleMedium,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    } // CompositionLocalProvider
}
