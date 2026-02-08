package com.spela.player.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

data class SpBottomBarItem(
    val label: String,
    val icon: String,
    val route: String,
)

@Composable
fun SpBottomBar(
    items: List<SpBottomBarItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SpSpacing.BottomBarHeight)
            .background(
                color = SpColor.Surface.copy(alpha = 0.97f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isSelected = item.route == selectedRoute
            SpBottomBarTab(
                item = item,
                isSelected = isSelected,
                onClick = { onItemSelected(item.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SpBottomBarTab(
    item: SpBottomBarItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) SpColor.Primary else SpColor.OnBackgroundTertiary,
        animationSpec = tween(200),
    )

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .focusable()
            .semantics {
                contentDescription = "${item.label} tab"
                role = Role.Tab
                selected = isSelected
            }
            .padding(vertical = SpSpacing.XSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isSelected) SpColor.PrimaryContainer else SpColor.Surface.copy(alpha = 0f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.icon,
                style = SpTypography.TitleLarge,
                color = contentColor,
            )
        }
        Text(
            text = item.label,
            style = SpTypography.LabelSmall,
            color = contentColor,
        )
    }
}
