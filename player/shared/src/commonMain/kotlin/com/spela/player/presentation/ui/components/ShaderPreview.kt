package com.spela.player.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.screen.drawShaderOverlay
import com.spela.player.presentation.ui.theme.SpColor

@Composable
fun ShaderPreview(
    imageUrl: String?,
    shader: ShaderPreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAsyncImagePainter(model = imageUrl)
    val painterState = painter.state

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Shader preview" }
            .testTag("shader-preview"),
    ) {
        // Fill background
        drawRect(color = SpColor.SurfaceVariant)

        if (painterState is AsyncImagePainter.State.Success) {
            val intrinsicSize = painter.intrinsicSize
            if (intrinsicSize.width > 0 && intrinsicSize.height > 0) {
                // Calculate scale-to-fit within the canvas
                val canvasW = size.width
                val canvasH = size.height
                val imgAspect = intrinsicSize.width / intrinsicSize.height
                val canvasAspect = canvasW / canvasH

                val dstW: Float
                val dstH: Float
                if (imgAspect > canvasAspect) {
                    dstW = canvasW
                    dstH = canvasW / imgAspect
                } else {
                    dstH = canvasH
                    dstW = canvasH * imgAspect
                }

                val dstX = ((canvasW - dstW) / 2).toInt()
                val dstY = ((canvasH - dstH) / 2).toInt()

                // Use the painter to draw with translation
                with(painter) {
                    drawContext.transform.translate(dstX.toFloat(), dstY.toFloat())
                    draw(
                        size = Size(dstW, dstH),
                        alpha = 1f,
                        colorFilter = null,
                    )
                    drawContext.transform.translate(-dstX.toFloat(), -dstY.toFloat())
                }

                // For the shader overlay, estimate a "source" pixel resolution
                // Most retro consoles are ~256x224 or similar
                val srcSize = IntSize(256, 224)
                val dstOffset = IntOffset(dstX, dstY)
                val dstSize = IntSize(dstW.toInt(), dstH.toInt())

                drawShaderOverlay(
                    shader = shader,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    srcSize = srcSize,
                )
            }
        }
    }
}
