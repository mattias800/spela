package com.spela.player.presentation.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GamePlatform
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

private const val COMPACT_LABEL_MAX_LENGTH = 12
private const val COMPACT_ID_MIN_LENGTH = 2
private const val COMPACT_ID_MAX_LENGTH = 6

internal fun compactPlatformLabel(consoleId: String, consoleName: String): String {
    val name = consoleName.ifBlank { consoleId.uppercase() }
    if (name.length <= COMPACT_LABEL_MAX_LENGTH) return name

    val id = consoleId.uppercase()
    return if (
        id.length in COMPACT_ID_MIN_LENGTH..COMPACT_ID_MAX_LENGTH &&
        id.all { it.isLetterOrDigit() || it == '-' }
    ) {
        id
    } else {
        name
    }
}

internal data class GamePlatformTarget(
    val gameId: String,
    val consoleId: String,
    val consoleName: String,
    val isPreferred: Boolean,
    val isCurrent: Boolean,
)

internal fun platformTargetsForGame(game: Game): List<GamePlatformTarget> {
    val fallback = GamePlatform(
        gameId = game.id,
        consoleId = game.consoleId,
        consoleName = game.consoleName,
        isPreferred = true,
    )
    val source = game.platforms.ifEmpty { listOf(fallback) }
    val targets = if (source.any { it.gameId == game.id }) {
        source
    } else {
        listOf(fallback.copy(isPreferred = false)) + source
    }.distinctBy { it.gameId }
    val preferredIndex = targets.indexOfFirst { it.isPreferred }.takeIf { it >= 0 }
    val currentIndex = targets.indexOfFirst { it.gameId == game.id }.takeIf { it >= 0 }
    val selectedIndex = preferredIndex
        ?: currentIndex
        ?: 0
    return targets.mapIndexed { index, platform ->
        GamePlatformTarget(
            gameId = platform.gameId,
            consoleId = platform.consoleId,
            consoleName = platform.consoleName,
            isPreferred = index == selectedIndex,
            isCurrent = platform.gameId == game.id,
        )
    }
}

internal fun preferredGameIdForGame(game: Game): String =
    platformTargetsForGame(game).firstOrNull { it.isPreferred }?.gameId ?: game.id

fun gamePlatformPillContent(
    game: Game,
    onPlatformSelected: ((String) -> Unit)?,
): (@Composable () -> Unit)? {
    if (onPlatformSelected == null || platformTargetsForGame(game).size <= 1) return null
    return { SpGamePlatformPills(game = game, onPlatformSelected = onPlatformSelected) }
}

/**
 * ROLE component — compact platform navigation for a title-folded game card.
 *
 * Renders only when the backend supplied more than one platform target. The
 * current card's target is informational; alternate targets are focusable
 * buttons so d-pad/keyboard users can open that platform release directly.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpGamePlatformPills(
    game: Game,
    onPlatformSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targets = platformTargetsForGame(game)
    if (targets.size <= 1) return
    val reportCarouselFocus = LocalCarouselChildHorizontalFocusReporter.current
    DisposableEffect(reportCarouselFocus) {
        onDispose { reportCarouselFocus?.invoke(false) }
    }

    FlowRow(
        modifier = modifier
            .focusGroup()
            .onFocusChanged { state ->
                reportCarouselFocus?.invoke(state.hasFocus)
            }
            .testTag("game_platform_pills_${game.id}"),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        targets.forEach { platform ->
            val isCurrent = platform.isCurrent
            SpConsoleChip(
                consoleName = compactPlatformLabel(platform.consoleId, platform.consoleName),
                consoleColor = SpColor.Primary,
                onGradient = true,
                isSelected = isCurrent,
                onClick = if (isCurrent) null else {
                    { onPlatformSelected(platform.gameId) }
                },
                modifier = Modifier
                    .testTag("game_platform_pill_${game.id}_${platform.gameId}")
                    .semantics {
                        contentDescription = if (isCurrent) {
                            "Current platform ${platform.consoleName}"
                        } else {
                            "Open ${game.title} on ${platform.consoleName}"
                        }
                    },
            )
        }
    }
}
