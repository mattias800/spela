package com.spela.player.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.screen.drawShaderOverlay
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
fun ShaderPreview(
    imageUrl: String?,
    shader: ShaderPreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Shader preview" }
            .testTag("shader-preview")
            .background(SpColor.SurfaceVariant),
    ) {
        // Load the preview image using AsyncImage (same as cover art)
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Draw shader overlay on top
        Canvas(modifier = Modifier.fillMaxSize()) {
            val srcSize = IntSize(256, 224)
            val dstOffset = IntOffset(0, 0)
            val dstSize = IntSize(size.width.toInt(), size.height.toInt())

            drawShaderOverlay(
                shader = shader,
                dstOffset = dstOffset,
                dstSize = dstSize,
                srcSize = srcSize,
            )
        }
    }
}
