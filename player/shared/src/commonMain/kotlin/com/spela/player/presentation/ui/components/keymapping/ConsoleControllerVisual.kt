package com.spela.player.presentation.ui.components.keymapping

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ButtonInfo
import com.spela.player.domain.model.ConsoleButtonLayout
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.LibretroButtons

/**
 * State for a button on the controller visual.
 */
enum class ButtonVisualState {
    /** Not yet mapped (or no binding set). */
    UNMAPPED,
    /** Currently highlighted / listening for key press. */
    HIGHLIGHTED,
    /** Has a mapping assigned. */
    MAPPED,
}

/**
 * Positioned button region on a controller visual.
 * Coordinates are in fractional (0..1) space relative to the controller bounds.
 */
data class ButtonRegion(
    val info: ButtonInfo,
    val cx: Float,
    val cy: Float,
    val isRound: Boolean = true,
    val widthFraction: Float = 0.07f,
    val heightFraction: Float = 0.07f,
)

/**
 * Draws a stylized controller outline for a given console with interactive button regions.
 *
 * @param layout The console button layout defining which buttons exist
 * @param buttonStates Map of retroButtonId to visual state
 * @param highlightedButton The retroButtonId currently being mapped (pulsing highlight)
 * @param mappingLabels Map of retroButtonId to a short label showing the bound key name
 * @param onButtonClick Called when a button region is clicked/tapped
 * @param modifier Standard modifier
 */
@Composable
fun ConsoleControllerVisual(
    layout: ConsoleButtonLayout,
    buttonStates: Map<Int, ButtonVisualState>,
    highlightedButton: Int?,
    mappingLabels: Map<Int, String>,
    onButtonClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val regions = remember(layout.consoleId) { getButtonRegions(layout) }

    val pulseTransition = rememberInfiniteTransition(label = "controllerPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "controllerPulseAlpha",
    )

    Box(modifier = modifier) {
        // Controller body outline
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawControllerBody(layout.consoleId)
        }

        // Interactive button regions
        regions.forEach { region ->
            val state = buttonStates[region.info.retroButtonId] ?: ButtonVisualState.UNMAPPED
            val isHighlighted = highlightedButton == region.info.retroButtonId

            val targetColor = when {
                isHighlighted -> SpColor.Primary.copy(alpha = pulseAlpha)
                state == ButtonVisualState.MAPPED -> SpColor.Surface
                else -> SpColor.SurfaceBright
            }
            val borderColor by animateColorAsState(
                targetValue = when {
                    isHighlighted -> SpColor.Primary
                    state == ButtonVisualState.MAPPED -> SpColor.Divider
                    else -> SpColor.DividerLight
                },
                animationSpec = tween(150),
                label = "buttonBorderColor",
            )
            val bgColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(150),
                label = "buttonBgColor",
            )
            val label = mappingLabels[region.info.retroButtonId]

            ControllerButton(
                region = region,
                label = region.info.label,
                mappedKeyLabel = label,
                backgroundColor = bgColor,
                borderColor = borderColor,
                isHighlighted = isHighlighted,
                onClick = { onButtonClick(region.info.retroButtonId) },
            )
        }
    }
}

@Composable
private fun ControllerButton(
    region: ButtonRegion,
    label: String,
    mappedKeyLabel: String?,
    backgroundColor: Color,
    borderColor: Color,
    isHighlighted: Boolean,
    onClick: () -> Unit,
) {
    // Position relative to parent Box via fractional coordinates
    val shape = if (region.isRound) CircleShape else RoundedCornerShape(4.dp)
    val buttonWidth = (region.widthFraction * 400).dp
    val buttonHeight = (region.heightFraction * 300).dp

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = ((region.cx - region.widthFraction / 2) * 400).dp.roundToPx(),
                    y = ((region.cy - region.heightFraction / 2) * 300).dp.roundToPx(),
                )
            }
            .size(width = buttonWidth, height = buttonHeight)
            .spFocusRing(shape = shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .focusable()
            .semantics {
                contentDescription = if (mappedKeyLabel != null) {
                    "$label mapped to $mappedKeyLabel"
                } else {
                    "$label, unmapped"
                }
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = backgroundColor,
                cornerRadius = if (region.isRound) CornerRadius(size.width / 2) else CornerRadius(4f),
            )
            drawRoundRect(
                color = borderColor,
                cornerRadius = if (region.isRound) CornerRadius(size.width / 2) else CornerRadius(4f),
                style = Stroke(width = 1.5f),
            )
        }
        Text(
            text = mappedKeyLabel ?: label,
            style = SpTypography.LabelSmall,
            color = if (isHighlighted) SpColor.OnPrimary else SpColor.OnSurface,
        )
    }
}

/**
 * Draws the controller body outline.
 * Uses a simplified rounded rectangle with grip extensions.
 */
private fun DrawScope.drawControllerBody(consoleId: String) {
    val w = size.width
    val h = size.height
    val bodyColor = SpColor.SurfaceVariant
    val outlineColor = SpColor.Divider

    // Main body rounded rect
    val bodyLeft = w * 0.1f
    val bodyTop = h * 0.15f
    val bodyWidth = w * 0.8f
    val bodyHeight = h * 0.55f
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(20f, 20f),
    )
    drawRoundRect(
        color = outlineColor,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(20f, 20f),
        style = Stroke(width = 1.5f),
    )

    // Left grip
    val gripPath = Path().apply {
        moveTo(bodyLeft + 10f, bodyTop + bodyHeight - 10f)
        lineTo(bodyLeft - 5f, bodyTop + bodyHeight + h * 0.15f)
        quadraticTo(
            bodyLeft - 8f, bodyTop + bodyHeight + h * 0.2f,
            bodyLeft + 20f, bodyTop + bodyHeight + h * 0.18f,
        )
        lineTo(bodyLeft + w * 0.15f, bodyTop + bodyHeight - 5f)
        close()
    }
    drawPath(gripPath, bodyColor)
    drawPath(gripPath, outlineColor, style = Stroke(width = 1.5f))

    // Right grip (mirrored)
    val rightGripPath = Path().apply {
        val rightEdge = bodyLeft + bodyWidth
        moveTo(rightEdge - 10f, bodyTop + bodyHeight - 10f)
        lineTo(rightEdge + 5f, bodyTop + bodyHeight + h * 0.15f)
        quadraticTo(
            rightEdge + 8f, bodyTop + bodyHeight + h * 0.2f,
            rightEdge - 20f, bodyTop + bodyHeight + h * 0.18f,
        )
        lineTo(rightEdge - w * 0.15f, bodyTop + bodyHeight - 5f)
        close()
    }
    drawPath(rightGripPath, bodyColor)
    drawPath(rightGripPath, outlineColor, style = Stroke(width = 1.5f))
}

/**
 * Builds a map of button visual states from a layout and mapping state.
 * Used by both KeyMappingScreen and KeyMappingWizard to determine which
 * buttons are mapped, highlighted, or unmapped.
 */
internal fun buildButtonStates(
    layout: ConsoleButtonLayout,
    currentBindings: Map<Int, Int>,
    currentMappingButton: Int?,
): Map<Int, ButtonVisualState> = buildMap {
    layout.buttons.forEach { btn ->
        val isMapped = currentBindings.containsKey(btn.retroButtonId)
        val isHighlighted = currentMappingButton == btn.retroButtonId
        put(btn.retroButtonId, when {
            isHighlighted -> ButtonVisualState.HIGHLIGHTED
            isMapped -> ButtonVisualState.MAPPED
            else -> ButtonVisualState.UNMAPPED
        })
    }
}

/**
 * Returns button regions positioned on a standard controller layout.
 * The layout adapts based on which buttons the console uses.
 */
private fun getButtonRegions(layout: ConsoleButtonLayout): List<ButtonRegion> {
    val buttonIds = layout.buttons.map { it.retroButtonId }.toSet()
    val buttonMap = layout.buttons.associateBy { it.retroButtonId }

    return buildList {
        // D-pad: left side of controller
        if (LibretroButtons.UP in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.UP]!!, 0.25f, 0.30f))
        if (LibretroButtons.DOWN in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.DOWN]!!, 0.25f, 0.50f))
        if (LibretroButtons.LEFT in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.LEFT]!!, 0.17f, 0.40f))
        if (LibretroButtons.RIGHT in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.RIGHT]!!, 0.33f, 0.40f))

        // Face buttons: right side of controller (diamond pattern)
        if (LibretroButtons.A in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.A]!!, 0.83f, 0.40f))
        if (LibretroButtons.B in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.B]!!, 0.75f, 0.50f))
        if (LibretroButtons.X in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.X]!!, 0.75f, 0.30f))
        if (LibretroButtons.Y in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.Y]!!, 0.67f, 0.40f))

        // Start/Select: center of controller
        if (LibretroButtons.START in buttonIds)
            add(ButtonRegion(
                buttonMap[LibretroButtons.START]!!,
                0.55f, 0.55f,
                isRound = false,
                widthFraction = 0.10f,
                heightFraction = 0.05f,
            ))
        if (LibretroButtons.SELECT in buttonIds)
            add(ButtonRegion(
                buttonMap[LibretroButtons.SELECT]!!,
                0.42f, 0.55f,
                isRound = false,
                widthFraction = 0.10f,
                heightFraction = 0.05f,
            ))

        // Shoulder buttons: top of controller
        if (LibretroButtons.L in buttonIds)
            add(ButtonRegion(
                buttonMap[LibretroButtons.L]!!,
                0.20f, 0.18f,
                isRound = false,
                widthFraction = 0.12f,
                heightFraction = 0.06f,
            ))
        if (LibretroButtons.R in buttonIds)
            add(ButtonRegion(
                buttonMap[LibretroButtons.R]!!,
                0.80f, 0.18f,
                isRound = false,
                widthFraction = 0.12f,
                heightFraction = 0.06f,
            ))

        // L2/R2 triggers
        if (LibretroButtons.L2 in buttonIds)
            add(ButtonRegion(
                buttonMap[LibretroButtons.L2]!!,
                0.20f, 0.10f,
                isRound = false,
                widthFraction = 0.10f,
                heightFraction = 0.05f,
            ))
        if (LibretroButtons.R2 in buttonIds)
            add(ButtonRegion(
                buttonMap[LibretroButtons.R2]!!,
                0.80f, 0.10f,
                isRound = false,
                widthFraction = 0.10f,
                heightFraction = 0.05f,
            ))

        // L3/R3 sticks
        if (LibretroButtons.L3 in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.L3]!!, 0.35f, 0.65f))
        if (LibretroButtons.R3 in buttonIds)
            add(ButtonRegion(buttonMap[LibretroButtons.R3]!!, 0.65f, 0.65f))
    }
}
