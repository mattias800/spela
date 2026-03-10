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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.spela.player.domain.model.MoodDefinition
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.parseHexColor

@Composable
fun MoodPicker(
    moods: List<MoodDefinition>,
    onMoodSelected: (moodId: String, moodName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("mood_picker"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        itemsIndexed(moods, key = { _, item -> item.id }) { _, item ->
            MoodCard(
                mood = item,
                onClick = { onMoodSelected(item.id, item.name) },
            )
        }
    }
}

@Composable
private fun MoodCard(
    mood: MoodDefinition,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)
    val gradientColors = mood.gradient.map { parseHexColor(it, Color(0xFF424242)) }.ifEmpty {
        listOf(Color(0xFF424242), Color(0xFF616161))
    }

    SpCard(
        modifier = Modifier
            .width(200.dp)
            .height(100.dp)
            .testTag("mood_card_${mood.id}")
            .semantics {
                contentDescription = "${mood.name}, ${mood.description}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(shape)
                .background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(SpSpacing.Medium),
            ) {
                Text(
                    text = mood.icon,
                    style = SpTypography.HeadlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = mood.name,
                    style = SpTypography.TitleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = mood.description,
                    style = SpTypography.LabelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun MoodPickerSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 4,
) {
    LazyRow(
        modifier = modifier.testTag("mood_picker_skeleton"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(count) {
            SpShimmer(
                modifier = Modifier
                    .width(200.dp)
                    .clip(RoundedCornerShape(SpSpacing.CardCornerRadius)),
                width = 200.dp,
                height = 100.dp,
            )
        }
    }
}
