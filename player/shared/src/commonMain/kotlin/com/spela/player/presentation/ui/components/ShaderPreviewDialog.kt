package com.spela.player.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.min
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun ShaderPreviewDialog(
    visible: Boolean,
    imageUrl: String?,
    shader: ShaderPreset,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(SpSpacing.XLarge),
            contentAlignment = Alignment.Center,
        ) {
            // Constrain preview to fit within available space while maintaining 4:3 aspect ratio
            val maxPreviewWidth = min(maxWidth, maxHeight * 4f / 3f)

            ShaderPreview(
                imageUrl = imageUrl,
                shader = shader,
                onClick = onDismiss,
                modifier = Modifier.widthIn(max = maxPreviewWidth),
            )

            Text(
                text = "Tap to close",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = SpSpacing.XLarge),
            )
        }
    }
}
