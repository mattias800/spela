package com.spela.player.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * DESIGN component — a link-shaped action that sits in the
 * `titleTrailing` slot of an [SpTitledSection].
 *
 * Visually: brand-coloured `LabelLarge` text inside a small clip-padded
 * tap target with the standard gamepad focus ring. Use this whenever a
 * section's header has a "See all" / "Browse gallery" / "Edit"-style
 * link in its top-right corner. Named after the *role* (header link of
 * a section) rather than the purpose ("see all"), so future uses
 * beyond pagination forwards have a consistent home.
 *
 * For heavier header-actions (a primary CTA button next to the title,
 * like "New session" or "Edit showcase") prefer `SpButton` / its
 * Ghost variant — those carry their own visual weight and shouldn't
 * route through here.
 *
 * @param text Displayed label.
 * @param onClick Invoked on click / Enter when focused.
 * @param modifier Forwarded.
 * @param contentDescription Screen-reader text. Defaults to [text].
 *   Pass an enriched value when the same [text] (e.g. "See all")
 *   appears on multiple sections in one screen — e.g.
 *   `contentDescription = "See all Play Later"`.
 */
@Composable
fun SpSectionLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = SpTypography.LabelLarge,
        color = SpColor.Link,
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.Small))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .gamepadFocusable(
                shape = RoundedCornerShape(SpSpacing.Small),
                interactionSource = interactionSource,
                addFocusable = false,
            )
            .padding(SpSpacing.Small)
            .semantics {
                this.contentDescription = contentDescription ?: text
                role = Role.Button
            },
    )
}
