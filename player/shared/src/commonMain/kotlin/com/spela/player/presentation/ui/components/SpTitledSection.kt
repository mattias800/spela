package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Reusable section wrapper with a title and content slot.
 *
 * Renders: optional top spacing + title (HeadlineSmall) + bottom spacing + content.
 *
 * @param title Section heading text.
 * @param modifier Modifier applied to the outer Column.
 * @param includeTopSpacing Whether to add [SpSpacing.XXLarge] above the title (default true).
 * @param titleTrailing Optional composable rendered beside the title in a Row (e.g. a count badge).
 * @param content Section body.
 */
@Composable
fun SpTitledSection(
    title: String,
    modifier: Modifier = Modifier,
    includeTopSpacing: Boolean = true,
    titleTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        if (includeTopSpacing) {
            Spacer(Modifier.height(SpSpacing.XXLarge))
        }
        if (titleTrailing != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                Text(
                    text = title,
                    style = SpTypography.HeadlineSmall,
                    color = SpColor.OnBackground,
                    modifier = Modifier.semantics { heading() },
                )
                titleTrailing()
            }
        } else {
            Text(
                text = title,
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(SpSpacing.Medium))
        content()
    }
}
