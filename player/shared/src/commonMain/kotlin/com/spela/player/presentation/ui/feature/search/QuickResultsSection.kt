package com.spela.player.presentation.ui.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.spela.player.domain.model.SearchSuggestion
import com.spela.player.domain.model.SuggestionNavigationType
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun QuickResultsSection(
    suggestions: List<SearchSuggestion>,
    onGameSelected: (String) -> Unit,
    onConsoleSelected: (String) -> Unit,
    onDeveloperSelected: (String) -> Unit,
    onPublisherSelected: (String) -> Unit,
    onCollectionSelected: (String) -> Unit,
    onSeriesSelected: (String, String) -> Unit,
    onFranchiseSelected: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_results_section"),
    ) {
        Text(
            text = "Quick results",
            style = SpTypography.TitleMedium,
            color = SpColor.OnBackgroundTertiary,
            modifier = Modifier
                .padding(
                    horizontal = SpSpacing.ScreenHorizontal,
                    vertical = SpSpacing.Small,
                ),
        )

        suggestions.forEach { suggestion ->
            QuickResultItem(
                suggestion = suggestion,
                onClick = {
                    dispatchNavigation(
                        navigationType = suggestion.navigationType,
                        onGameSelected = onGameSelected,
                        onConsoleSelected = onConsoleSelected,
                        onDeveloperSelected = onDeveloperSelected,
                        onPublisherSelected = onPublisherSelected,
                        onCollectionSelected = onCollectionSelected,
                        onSeriesSelected = onSeriesSelected,
                        onFranchiseSelected = onFranchiseSelected,
                    )
                },
            )
        }
    }
}

@Composable
private fun QuickResultItem(
    suggestion: SearchSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XXSmall)
            .testTag("quick_result_${suggestion.type.lowercase()}_${suggestion.id}"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            // Thumbnail / icon
            QuickResultThumbnail(suggestion = suggestion)

            // Name and subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (suggestion.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = suggestion.subtitle,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Type badge using SpChip
            SpChip(
                text = suggestion.type,
                isSelected = true,
            )
        }
    }
}

@Composable
private fun QuickResultThumbnail(
    suggestion: SearchSuggestion,
    modifier: Modifier = Modifier,
) {
    val isGame = suggestion.navigationType is SuggestionNavigationType.Game

    if (isGame && suggestion.imageUrl != null) {
        SpCoverArt(
            imageUrl = suggestion.imageUrl,
            contentDescription = "${suggestion.name} cover",
            modifier = modifier
                .width(40.dp)
                .height(54.dp),
            aspectRatio = 0.75f,
            cornerRadius = SpSpacing.RadiusSmall,
        )
    } else {
        Box(
            modifier = modifier
                .size(40.dp)
                .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                .background(SpColor.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val isConsole = suggestion.navigationType is SuggestionNavigationType.Console
            val isCollection = suggestion.navigationType is SuggestionNavigationType.Collection

            when {
                isConsole && !suggestion.imageUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = suggestion.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
                isCollection -> {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = SpColor.OnBackgroundSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                isConsole -> {
                    Icon(
                        imageVector = Icons.Filled.Gamepad,
                        contentDescription = null,
                        tint = SpColor.OnBackgroundSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                else -> {
                    Text(
                        text = suggestion.name.take(2).uppercase(),
                        style = SpTypography.LabelMedium,
                        color = SpColor.OnBackgroundSecondary,
                    )
                }
            }
        }
    }
}

private fun dispatchNavigation(
    navigationType: SuggestionNavigationType,
    onGameSelected: (String) -> Unit,
    onConsoleSelected: (String) -> Unit,
    onDeveloperSelected: (String) -> Unit,
    onPublisherSelected: (String) -> Unit,
    onCollectionSelected: (String) -> Unit,
    onSeriesSelected: (String, String) -> Unit,
    onFranchiseSelected: (String, String) -> Unit,
) {
    when (navigationType) {
        is SuggestionNavigationType.Game -> onGameSelected(navigationType.id)
        is SuggestionNavigationType.Console -> onConsoleSelected(navigationType.id)
        is SuggestionNavigationType.Developer -> onDeveloperSelected(navigationType.name)
        is SuggestionNavigationType.Publisher -> onPublisherSelected(navigationType.name)
        is SuggestionNavigationType.Collection -> onCollectionSelected(navigationType.id)
        is SuggestionNavigationType.Series -> onSeriesSelected(navigationType.id, navigationType.name)
        is SuggestionNavigationType.Franchise -> onFranchiseSelected(navigationType.id, navigationType.name)
    }
}
