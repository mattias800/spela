package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * CONTENT component — horizontal card with icon/avatar on the left and text on the right.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Matches the layout of [SpWideGameCard] but with an icon slot instead of cover art.
 * Used for non-game search results (consoles, developers, collections, etc).
 *
 * @param title Primary text (TitleLarge).
 * @param subtitle Secondary text below title.
 * @param onClick Click handler.
 * @param icon Composable rendered inside the icon box (e.g. Icon, Text with initials).
 * @param iconSize Size of the icon box.
 * @param testTag Optional test tag.
 * @param extraContent Optional composable below the subtitle.
 */
@Composable
fun SpWideIconCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    testTag: String? = null,
    extraContent: (@Composable () -> Unit)? = null,
) {
    SpCard(
        modifier = modifier
            .fillMaxWidth()
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
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                    .background(SpColor.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
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
