package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.spela.player.domain.model.Console
import com.spela.player.presentation.ui.components.SpGradientCard
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun ConsolesGrid(
    consoles: List<Console>,
    onConsoleSelected: (String) -> Unit,
    consolesWithMissingBios: Set<String> = emptySet(),
    columnsPerRow: Int = 2,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        consoles.chunked(columnsPerRow).forEach { rowConsoles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                rowConsoles.forEach { console ->
                    ConsoleCard(
                        console = console,
                        onClick = { onConsoleSelected(console.id) },
                        hasMissingBios = console.id in consolesWithMissingBios,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columnsPerRow - rowConsoles.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun ConsoleCard(
    console: Console,
    onClick: () -> Unit,
    hasMissingBios: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val consoleColor = getConsoleColor(console.colorTheme)
    val biosDesc = if (hasMissingBios) ", BIOS missing" else ""

    SpGradientCard(
        modifier = modifier
            .height(100.dp)
            .semantics {
                contentDescription = "${console.name}, ${console.gameCount} games$biosDesc"
                role = Role.Button
            },
        onClick = onClick,
        gradientColors = listOf(
            consoleColor.copy(alpha = 0.3f),
            consoleColor.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    Text(
                        text = console.name,
                        style = SpTypography.TitleLarge,
                        color = SpColor.OnBackground,
                    )
                    if (hasMissingBios) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "BIOS missing for ${console.name}",
                            tint = SpColor.Warning,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = "${console.gameCount} games",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
                )
            }
            if (console.iconUrl.isNotEmpty()) {
                AsyncImage(
                    model = console.iconUrl,
                    contentDescription = "${console.name} icon",
                    modifier = Modifier.size(56.dp),
                    alpha = 0.7f,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = "${console.name} icon",
                    tint = SpColor.OnBackground.copy(alpha = 0.3f),
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
internal fun ConsoleHeroBanner(
    console: Console,
    modifier: Modifier = Modifier,
) {
    val consoleColor = getConsoleColor(console.colorTheme)
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)

    // Diagonal gradient background using console brand color
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            consoleColor,
            consoleColor.copy(alpha = 0.6f),
        ),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    // Depth overlay: transparent top → black/30 bottom
    val overlayBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.04f),
            Color.Transparent,
            Color.Black.copy(alpha = 0.3f),
        ),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundBrush)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .semantics {
                contentDescription = "${console.name}, ${console.gameCount} games"
            },
    ) {
        // Watermark icon (top-end, large, very faint)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 24.dp, y = (-24).dp)
                .alpha(0.07f),
        ) {
            if (console.iconUrl.isNotEmpty()) {
                AsyncImage(
                    model = console.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(180.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(180.dp),
                )
            }
        }

        // Gradient overlay for depth
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(overlayBrush),
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.XLarge, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo or text fallback
            var logoFailed by remember { mutableStateOf(false) }

            if (console.logoUrl.isNotEmpty() && !logoFailed) {
                AsyncImage(
                    model = console.logoUrl,
                    contentDescription = console.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 80.dp),
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                )
            }

            if (console.logoUrl.isEmpty() || logoFailed) {
                Text(
                    text = console.name,
                    style = SpTypography.HeadlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            // Metadata row
            Row(
                modifier = Modifier.padding(top = SpSpacing.Default),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${console.gameCount} ${if (console.gameCount == 1) "game" else "games"}",
                    style = SpTypography.BodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                if (console.saveStateSupport) {
                    MetadataBadge(
                        icon = { Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.9f)) },
                        label = "Save states",
                    )
                }
                if (console.browserPlayable) {
                    MetadataBadge(
                        icon = { Icon(Icons.Filled.Language, null, Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.9f)) },
                        label = "Browser play",
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(
    icon: @Composable () -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier
            .background(
                Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(SpSpacing.RadiusPill),
            )
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

internal fun getConsoleColor(colorTheme: String?): Color {
    if (colorTheme == null) return SpColor.Primary
    // Try parsing as hex color first (backend sends "#e53e3e" format)
    if (colorTheme.startsWith("#")) {
        return try {
            val hex = colorTheme.removePrefix("#")
            val colorLong = when (hex.length) {
                6 -> (0xFF000000 or hex.toLong(16))
                8 -> hex.toLong(16)
                else -> null
            }
            if (colorLong != null) Color(colorLong.toInt()) else SpColor.Primary
        } catch (_: NumberFormatException) {
            SpColor.Primary
        }
    }
    // Fallback to name matching for backwards compatibility
    return when (colorTheme.lowercase()) {
        "nes" -> SpColor.ConsoleNes
        "snes" -> SpColor.ConsoleSnes
        "gameboy", "gb", "gbc" -> SpColor.ConsoleGameBoy
        "gba" -> SpColor.ConsoleGba
        "n64" -> SpColor.ConsoleN64
        "nds" -> SpColor.ConsoleNds
        "sega", "genesis", "megadrive" -> SpColor.ConsoleSega
        "psx", "playstation" -> SpColor.ConsolePsx
        "psp" -> SpColor.ConsolePsp
        "arcade", "mame" -> SpColor.ConsoleArcade
        else -> SpColor.Primary
    }
}
