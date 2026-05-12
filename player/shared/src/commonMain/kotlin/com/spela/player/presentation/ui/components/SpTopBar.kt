package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.TestTags
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
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
    onGradient: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    // Hide in gamepad mode — user navigates with B button for back,
    // and action buttons should be placed in the screen content instead.
    if (LocalInputMode.current == InputMode.GAMEPAD) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SpColor.Surface.copy(alpha = 0f)),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(LocalTitleBarInset.current))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SpSpacing.TopBarHeight)
                .padding(horizontal = SpSpacing.ScreenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                val backInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .testTag(TestTags.BACK_BUTTON)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (onGradient) Color.Black.copy(alpha = 0.30f) else SpColor.SurfaceVariant)
                        .clickable(
                            interactionSource = backInteractionSource,
                            indication = null,
                            onClick = onBack,
                        )
                        .gamepadFocusable(
                            shape = CircleShape,
                            interactionSource = backInteractionSource,
                            addFocusable = false,
                        )
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
