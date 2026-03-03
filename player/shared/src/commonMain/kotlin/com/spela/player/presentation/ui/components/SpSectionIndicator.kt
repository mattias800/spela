package com.spela.player.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
fun SpSectionIndicator(
    activeTab: BottomNavTab,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = LocalAnimationsEnabled.current

    AnimatedVisibility(
        visible = visible,
        enter = if (animationsEnabled) fadeIn() + slideInVertically(initialOffsetY = { -it }) else EnterTransition.None,
        exit = if (animationsEnabled) fadeOut() + slideOutVertically(targetOffsetY = { -it }) else ExitTransition.None,
        modifier = modifier.semantics { contentDescription = "Section indicator" },
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = SpColor.SurfaceElevated.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "L1",
                color = SpColor.OnBackgroundSecondary,
                fontSize = 12.sp,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val isActive = tab == activeTab
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = if (isActive) "Section: ${tab.label}, active" else "Section: ${tab.label}",
                        tint = if (isActive) SpColor.Primary else SpColor.OnBackgroundTertiary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                text = "R1",
                color = SpColor.OnBackgroundSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
