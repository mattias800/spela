package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Theme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Theme gradient colors — each theme gets a distinctive gradient based on its index.
 */
private val themeGradients = listOf(
    listOf(Color(0xFF6C5CE7), Color(0xFF4834D4)),  // Indigo
    listOf(Color(0xFFFF6B81), Color(0xFFE84563)),  // Rose
    listOf(Color(0xFF00D2FF), Color(0xFF009FCC)),  // Cyan
    listOf(Color(0xFF00C853), Color(0xFF009624)),  // Green
    listOf(Color(0xFFFF6F00), Color(0xFFE65100)),  // Orange
    listOf(Color(0xFFAA00FF), Color(0xFF7B1FA2)),  // Purple
    listOf(Color(0xFF304FFE), Color(0xFF1A237E)),  // Deep blue
    listOf(Color(0xFFFFD600), Color(0xFFC8A200)),  // Gold
)

@Composable
fun ThemeGrid(
    themes: List<Theme>,
    onThemeSelected: (themeId: String, themeName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        itemCount = themes.size,
        modifier = modifier.testTag("theme_grid"),
    ) { index, focusRequester ->
        val theme = themes[index]
        val gradientIndex = index % themeGradients.size
        Box(modifier = Modifier.focusRequester(focusRequester)) {
            ThemeCard(
                theme = theme,
                gradientColors = themeGradients[gradientIndex],
                onClick = { onThemeSelected(theme.id, theme.name) },
            )
        }
    }
}

@Composable
private fun ThemeCard(
    theme: Theme,
    gradientColors: List<Color>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)

    SpCard(
        modifier = Modifier
            .width(160.dp)
            .height(90.dp)
            .testTag("theme_card_${theme.id}")
            .semantics {
                contentDescription = "${theme.name}, ${theme.gameCount} games"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(shape)
                .background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(SpSpacing.Medium),
            ) {
                Text(
                    text = theme.name,
                    style = SpTypography.TitleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "${theme.gameCount} games",
                    style = SpTypography.LabelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun ThemeGridSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    LazyRow(
        modifier = modifier.testTag("theme_grid_skeleton"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(count) {
            com.spela.player.presentation.ui.components.SpShimmer(
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(SpSpacing.CardCornerRadius)),
                width = 160.dp,
                height = 90.dp,
            )
        }
    }
}
