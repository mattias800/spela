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
 * Reusable section wrapper with a title and card-styled content slot.
 *
 * Renders: optional top spacing + a card container with icon + title header inside,
 * followed by the content. The card uses a semi-transparent black background that
 * darkens the gradient behind it slightly.
 *
 * @param title Section heading text.
 * @param modifier Modifier applied to the outer Column.
 * @param icon Optional icon displayed before the title in accent color.
 * @param includeTopSpacing Whether to add [SpSpacing.XXLarge] above the title (default true).
 * @param titleTrailing Optional composable rendered beside the title in a Row (e.g. a count badge).
 * @param content Section body rendered inside the card below the header.
 */
@Composable
fun SpTitledSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    includeTopSpacing: Boolean = true,
    titleTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)

    Column(modifier = modifier) {
        if (includeTopSpacing) {
            Spacer(Modifier.height(SpSpacing.XXLarge))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.18f), shape)
                .border(1.dp, SpColor.Divider.copy(alpha = 0.4f), shape)
                .padding(SpSpacing.Default),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
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
                        modifier = Modifier.semantics { heading() },
                    )
                    if (titleTrailing != null) {
                        titleTrailing()
                    }
                }
                Spacer(Modifier.height(SpSpacing.Medium))
                content()
            }
        }
    }
}
