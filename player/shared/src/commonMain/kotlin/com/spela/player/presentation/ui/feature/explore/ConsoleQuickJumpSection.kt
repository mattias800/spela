package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ConsoleHighlight
import com.spela.player.presentation.ui.components.SpConsoleTile
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

/** ROLE component — a console tile in Browse by Console. Delegates to [SpConsoleTile]. */
@Composable
private fun ConsoleQuickJumpCard(
    console: ConsoleHighlight,
    onClick: () -> Unit,
) {
    SpConsoleTile(
        name = console.name,
        gameCount = console.gameCount,
        accentColor = parseHexColor(console.colorTheme, SpColor.Primary),
        logoUrl = console.logoUrl,
        onClick = onClick,
        testTag = "console_card_${console.id}",
    )
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
