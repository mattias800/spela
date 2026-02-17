package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import com.spela.player.presentation.ui.gamepad.spFocusRing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    titleLeadingContent: @Composable (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SpColor.Surface.copy(alpha = 0.95f)),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SpSpacing.TopBarHeight)
                .padding(horizontal = SpSpacing.ScreenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .spFocusRing(shape = CircleShape)
                        .clip(CircleShape)
                        .background(SpColor.SurfaceVariant)
                        .clickable(onClick = onBack)
                        .focusable()
                        .semantics {
                            contentDescription = "Go back"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = SpColor.OnSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(SpSpacing.Medium))
            }

            if (titleLeadingContent != null) {
                titleLeadingContent()
                Spacer(Modifier.width(SpSpacing.Small))
            }

            Text(
                text = title,
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}
