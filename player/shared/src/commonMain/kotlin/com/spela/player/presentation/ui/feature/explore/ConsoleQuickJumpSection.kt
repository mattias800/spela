package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ConsoleHighlight
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.focusRequester
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpConsoleTile
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.util.parseHexColor

@Composable
fun ConsoleQuickJumpSection(
    consoles: List<ConsoleHighlight>,
    onConsoleSelected: (consoleId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        itemCount = consoles.size,
        modifier = modifier.testTag("console_quick_jump"),
        memoryKey = "explore_console_quick_jump",
        itemKey = { consoles[it].id },
    ) { index, focusRequester ->
        Box(modifier = Modifier.focusRequester(focusRequester)) {
            ConsoleQuickJumpCard(
                console = consoles[index],
                onClick = { onConsoleSelected(consoles[index].id) },
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
