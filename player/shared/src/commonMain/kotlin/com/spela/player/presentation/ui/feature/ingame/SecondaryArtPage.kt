package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.presentation.ui.feature.library.getConsoleGradient
import com.spela.player.presentation.ui.screen.formatSessionDuration
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Art Display page for the secondary screen companion.
 *
 * Shows hero artwork (or a console gradient fallback) at the top, followed by the
 * game title, session timer, and an optional game info card with metadata such as
 * developer, publisher, release year, genre, rating, and description.
 *
 * The entire page scrolls vertically so all metadata is accessible even on small
 * secondary displays.
 */
@Composable
fun SecondaryArtPage(
    heroUrl: String?,
    gameTitle: String,
    sessionElapsedSeconds: Long,
    consoleId: String,
    consoleColorTheme: String?,
    gameDescription: String? = null,
    gameDeveloper: String? = null,
    gamePublisher: String? = null,
    gameReleaseDate: String? = null,
    gameGenre: String? = null,
    gameRating: Double = 0.0,
    gamePlayers: Int = 0,
) {
    val timeText = formatSessionDuration(sessionElapsedSeconds)
    val (gradientFrom, _) = getConsoleGradient(consoleId, consoleColorTheme)

    val hasGameInfo = !gameDeveloper.isNullOrEmpty() || !gamePublisher.isNullOrEmpty() || !gameGenre.isNullOrEmpty() ||
        !gameReleaseDate.isNullOrEmpty() || !gameDescription.isNullOrEmpty() || gamePlayers > 0 || gameRating > 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background)
            .verticalScroll(rememberScrollState())
            .semantics {
                contentDescription = "Art display: $gameTitle"
            },
    ) {
        if (heroUrl != null) {
            // Hero image at fixed aspect ratio
            SubcomposeAsyncImage(
                model = heroUrl,
                contentDescription = "Hero artwork for $gameTitle",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(SpColor.SurfaceVariant),
                    )
                },
                error = {
                    // On error, show console gradient header instead
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        gradientFrom.copy(alpha = 0.15f),
                                        SpColor.Background,
                                    ),
                                )
                            ),
                    )
                },
            )
        } else {
            // Fallback: decorative console gradient area (title/timer already in CompanionHeader)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                gradientFrom.copy(alpha = 0.15f),
                                SpColor.Background,
                            ),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = consoleId.uppercase(),
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }

        // Title + timer row (shown below hero image, or below fallback header content is already in the header)
        if (heroUrl != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = gameTitle,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeText,
                    style = SpTypography.LabelLarge,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }

        // Game info card
        if (hasGameInfo) {
            GameInfoCard(
                gameDeveloper = gameDeveloper,
                gamePublisher = gamePublisher,
                gameReleaseDate = gameReleaseDate,
                gameGenre = gameGenre,
                gameRating = gameRating,
                gamePlayers = gamePlayers,
                gameDescription = gameDescription,
            )
        }
    }
}

/**
 * Card displaying game metadata: developer, publisher, release year, genre,
 * player count, rating, and description.
 *
 * Only rows with non-null, non-empty values are rendered.
 */
@Composable
private fun GameInfoCard(
    gameDeveloper: String?,
    gamePublisher: String?,
    gameReleaseDate: String?,
    gameGenre: String?,
    gameRating: Double,
    gamePlayers: Int,
    gameDescription: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
            .background(SpColor.Card)
            .padding(SpSpacing.Medium)
            .semantics {
                contentDescription = "Game information"
            },
    ) {
        var hasRow = false

        if (!gameDeveloper.isNullOrEmpty()) {
            MetadataRow(label = "Developer", value = gameDeveloper)
            hasRow = true
        }

        if (!gamePublisher.isNullOrEmpty()) {
            if (hasRow) Spacer(Modifier.height(SpSpacing.XSmall))
            MetadataRow(label = "Publisher", value = gamePublisher)
            hasRow = true
        }

        if (!gameReleaseDate.isNullOrEmpty()) {
            if (hasRow) Spacer(Modifier.height(SpSpacing.XSmall))
            MetadataRow(label = "Released", value = gameReleaseDate)
            hasRow = true
        }

        if (!gameGenre.isNullOrEmpty()) {
            if (hasRow) Spacer(Modifier.height(SpSpacing.XSmall))
            MetadataRow(label = "Genre", value = gameGenre)
            hasRow = true
        }

        if (gamePlayers > 0) {
            if (hasRow) Spacer(Modifier.height(SpSpacing.XSmall))
            val playersText = if (gamePlayers == 1) "1 Player" else "1-$gamePlayers Players"
            MetadataRow(label = "Players", value = playersText)
            hasRow = true
        }

        if (gameRating > 0.0) {
            if (hasRow) Spacer(Modifier.height(SpSpacing.XSmall))
            RatingRow(rating = gameRating)
            hasRow = true
        }

        if (!gameDescription.isNullOrEmpty()) {
            if (hasRow) {
                Spacer(Modifier.height(SpSpacing.Small))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SpColor.Divider),
                )
                Spacer(Modifier.height(SpSpacing.Small))
            }
            Text(
                text = gameDescription,
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A single metadata row with a label on the left and value on the right.
 */
@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackgroundTertiary,
        )
        Spacer(Modifier.width(SpSpacing.Small))
        Text(
            text = value,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Rating row showing a star character followed by the numeric rating.
 */
@Composable
private fun RatingRow(rating: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Rating",
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackgroundTertiary,
        )
        Row {
            Text(
                text = "\u2605 ",
                style = SpTypography.BodyMedium,
                color = SpColor.Rating,
            )
            Text(
                text = "%.1f".format(rating),
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackground,
            )
        }
    }
}
