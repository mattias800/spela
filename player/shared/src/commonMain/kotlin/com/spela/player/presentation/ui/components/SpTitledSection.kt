package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * CONTENT component — a card section with a title and content slot.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Renders a semi-transparent card with: icon + title header, then content below.
 *
 * Does NOT add outer spacing — the parent (e.g. [SpSectionList]) controls
 * gaps between sections via `Arrangement.spacedBy()`.
 *
 * @param title Section heading text.
 * @param modifier Modifier applied to the card Box.
 * @param icon Optional icon displayed before the title in accent color.
 * @param titleTrailing Optional composable beside the title (e.g. a link).
 * @param edgeToEdgeContent When true, content has no horizontal padding so
 *   carousels can extend to the card edges. Header retains horizontal padding.
 * @param content Section body rendered inside the card below the header.
 */
@Composable
fun SpTitledSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    titleTrailing: @Composable (() -> Unit)? = null,
    edgeToEdgeContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.18f), shape)
            .border(1.dp, SpColor.Divider.copy(alpha = 0.4f), shape)
            .let {
                if (edgeToEdgeContent) it.padding(vertical = SpSpacing.Default)
                else it.padding(SpSpacing.Default)
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = if (edgeToEdgeContent) Modifier.padding(horizontal = SpSpacing.Default) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SpColor.Accent,
                        modifier = Modifier.size(SpSpacing.IconDefault),
                    )
                }
                Text(
                    text = title,
                    style = SpTypography.HeadlineSmall,
                    color = SpColor.OnBackground,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (titleTrailing != null) {
                    titleTrailing()
                }
            }
            Spacer(Modifier.height(SpSpacing.Default))
            content()
        }
    }
}

