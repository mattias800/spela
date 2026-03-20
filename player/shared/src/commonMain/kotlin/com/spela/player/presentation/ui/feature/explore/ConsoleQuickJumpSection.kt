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
import coil3.compose.AsyncImage
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
            .width(160.dp)
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
                .height(100.dp)
                .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(SpSpacing.Medium),
            ) {
                if (console.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = console.logoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .height(28.dp)
                            .fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(SpSpacing.Small))
                Text(
                    text = console.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${console.gameCount} ${if (console.gameCount == 1) "game" else "games"}",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
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
                width = 160.dp,
                height = 100.dp,
            )
        }
    }
}
