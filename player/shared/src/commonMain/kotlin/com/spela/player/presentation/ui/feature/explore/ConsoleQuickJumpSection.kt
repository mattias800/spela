package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ConsoleHighlight
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.parseHexColor

@Composable
fun ConsoleQuickJumpSection(
    consoles: List<ConsoleHighlight>,
    onConsoleSelected: (consoleId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("console_quick_jump"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(consoles, key = { it.id }) { console ->
            ConsoleQuickJumpCard(
                console = console,
                onClick = { onConsoleSelected(console.id) },
            )
        }
    }
}

@Composable
private fun ConsoleQuickJumpCard(
    console: ConsoleHighlight,
    onClick: () -> Unit,
) {
    val accentColor = parseHexColor(console.colorTheme, SpColor.Primary)

    SpCard(
        modifier = Modifier
            .width(140.dp)
            .testTag("console_card_${console.id}")
            .semantics {
                contentDescription = "${console.name}, ${console.gameCount} games"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(SpSpacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(SpSpacing.XSmall))
                Text(
                    text = console.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${console.gameCount} games",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ConsoleQuickJumpSkeleton(
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("console_quick_jump_skeleton"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(5) {
            SpShimmer(
                modifier = Modifier.clip(RoundedCornerShape(SpSpacing.CardCornerRadius)),
                width = 140.dp,
                height = 90.dp,
            )
        }
    }
}
