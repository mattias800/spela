package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * CONTENT component — horizontal game card with cover on the left and text on the right.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Composes [SpCard] + [SpCoverArt] into a horizontal layout.
 * Used for cards that need more horizontal space (e.g. Continue Playing).
 *
 * Parallel to [SpGameCard] (vertical layout). Both use [SpCard] as their
 * design component. Choose based on layout needs:
 * - [SpGameCard] — vertical, compact, for shelves and grids
 * - [SpWideGameCard] — horizontal, spacious, for featured/prominent cards
 *
 * Does NOT accept a modifier parameter — the layout is strict.
 * Parent controls sizing via the [width] parameter only.
 *
 * @param extraContent Optional composable rendered below the subtitle
 *   (e.g. play time, console chip). Rendered inside the text column.
 */
@Composable
fun SpWideGameCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
    coverAspectRatio: Float = 0.75f,
    width: Dp = 280.dp,
    fillWidth: Boolean = false,
    coverHeight: Dp = 84.dp,
    testTag: String? = null,
    extraContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    SpCard(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width))
            .let { if (testTag != null) it.testTag(testTag) else it }
            .semantics {
                contentDescription = "$title, $subtitle"
                role = Role.Button
            },
        onClick = onClick,
        onGradient = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = coverUrl,
                contentDescription = "$title cover art",
                modifier = Modifier.height(coverHeight),
                cornerRadius = SpSpacing.RadiusMedium,
                aspectRatio = coverAspectRatio,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(SpSpacing.Small))
                    Text(
                        text = subtitle,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (extraContent != null) {
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    extraContent()
                }
            }
        }
    }
}
