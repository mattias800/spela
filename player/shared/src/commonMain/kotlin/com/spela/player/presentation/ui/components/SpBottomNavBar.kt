package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

enum class BottomNavTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Filled.Home),
    CONSOLES("Consoles", Icons.Filled.SportsEsports),
    COLLECTIONS("Collections", Icons.Filled.CollectionsBookmark),
    ACTIVITY("Activity", Icons.Filled.Notifications),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun SpBottomNavBar(
    activeTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SpColor.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavTab.entries.forEach { tab ->
                val isSelected = tab == activeTab
                val color = if (isSelected) SpColor.Primary else SpColor.OnBackgroundTertiary

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable { onTabSelected(tab) }
                        .semantics {
                            contentDescription = tab.label
                            role = Role.Tab
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = tab.label,
                            style = SpTypography.LabelSmall,
                            color = color,
                        )
                    }
                }
            }
        }
        // Safe area padding for devices with gesture navigation
        Box(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
