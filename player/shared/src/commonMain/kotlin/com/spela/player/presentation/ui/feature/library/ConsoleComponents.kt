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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
            .height(120.dp)
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
                    var logoFailed by remember { mutableStateOf(false) }

                    if (console.logoUrl.isNotEmpty() && !logoFailed) {
                        AsyncImage(
                            model = console.logoUrl,
                            contentDescription = console.name,
                            modifier = Modifier.weight(1f, fill = false).heightIn(max = 40.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            onError = { logoFailed = true },
                        )
                    } else {
                        Text(
                            text = console.name,
                            style = SpTypography.TitleLarge,
                            color = SpColor.OnBackground,
                        )
                    }
                    if (hasMissingBios) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "BIOS missing for ${console.name}",
                            tint = SpColor.Warning,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(SpSpacing.Small))
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
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)

    // Per-console gradient matching web UI's console-metadata.ts exactly
    val (gradientFrom, gradientTo) = getConsoleGradient(console.abbreviation, console.colorTheme)
    val gradientColors = listOf(gradientFrom, gradientTo)

    // Depth overlay matching web: from-black/30 via-transparent to-white/[0.04]
    // Web uses bg-gradient-to-t (bottom→top), so in vertical gradient: top=white/4, mid=transparent, bottom=black/30
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
            // CSS 135deg-equivalent gradient: fixed 45° diagonal (top-left → bottom-right)
            // regardless of aspect ratio, matching the visual appearance on the web.
            // d = (w + h) / 4 — the displacement from centre along both axes.
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val d = (size.width + size.height) * 0.25f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset(cx - d, cy - d),
                        end = Offset(cx + d, cy + d),
                    ),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .semantics {
                contentDescription = "${console.name}, ${console.gameCount} games"
            },
    ) {
        // Watermark icon: web uses -right-8 -top-8 (32px) at 224px (h-56 w-56), opacity 7%
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 32.dp, y = (-32).dp)
                .alpha(0.07f),
        ) {
            if (console.iconUrl.isNotEmpty()) {
                AsyncImage(
                    model = console.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(224.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(224.dp),
                )
            }
        }

        // Gradient overlay for depth
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(overlayBrush),
        )

        // Content: stats on left, logo + badges truly centered in full width
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.XLarge, vertical = 40.dp),
        ) {
            // Compact platform details on the left — capped width so they never
            // overlap the centred logo, with a fading bottom edge for overflow safety.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 96.dp)
                    .heightIn(max = 120.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawBehind {
                        // fade out bottom ~25% so overflow clips gracefully
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height * 0.75f,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                ConsoleInfoSection(console = console)
            }

            // Logo + metadata badges centered in full banner width
            Column(
                modifier = Modifier.fillMaxWidth(),
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

                // Metadata row: game count + badges
                Row(
                    modifier = Modifier.padding(top = SpSpacing.Default),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${console.gameCount} ${if (console.gameCount == 1) "game" else "games"}",
                        style = SpTypography.BodySmall,
                        fontWeight = FontWeight.Medium,
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
}

@Composable
internal fun MetadataBadge(
    icon: @Composable () -> Unit,
    label: String,
    backgroundColor: Color = Color.White.copy(alpha = 0.1f),
    textColor: Color = Color.White.copy(alpha = 0.9f),
) {
    Row(
        modifier = Modifier
            .background(
                backgroundColor,
                RoundedCornerShape(SpSpacing.RadiusPill),
            )
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

/** Darkens a color by mixing it towards black. [amount] 0f = unchanged, 1f = pure black. */
fun Color.darken(amount: Float): Color = copy(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
)

/**
 * Returns the hero banner gradient (from, to) for a console.
 * Colors match the web UI's console-metadata.ts Tailwind gradient pairs exactly.
 * Falls back to deriving a gradient from the console's colorTheme hex.
 */
fun getConsoleGradient(abbreviation: String, colorTheme: String?): Pair<Color, Color> {
    // Curated gradient pairs matching the web UI (Tailwind color values)
    val gradient = when (abbreviation.lowercase()) {
        "nes" -> Color(0xFFdc2626) to Color(0xFF7f1d1d)         // red-600 → red-900
        "snes" -> Color(0xFF9333ea) to Color(0xFF312e81)        // purple-600 → indigo-900
        "gb" -> Color(0xFF16a34a) to Color(0xFF14532d)          // green-600 → green-900
        "gbc" -> Color(0xFF14b8a6) to Color(0xFF166534)         // teal-500 → green-800
        "gba" -> Color(0xFF6366f1) to Color(0xFF6b21a8)         // indigo-500 → purple-800
        "n64" -> Color(0xFF22c55e) to Color(0xFF1d4ed8)         // green-500 → blue-700
        "nds" -> Color(0xFF9ca3af) to Color(0xFF374151)         // gray-400 → gray-700
        "sms" -> Color(0xFF3b82f6) to Color(0xFF1e40af)         // blue-500 → blue-800
        "gen" -> Color(0xFF1d4ed8) to Color(0xFF000000)         // blue-700 → black
        "sat" -> Color(0xFF4b5563) to Color(0xFF111827)         // gray-600 → gray-900
        "psx" -> Color(0xFF6b7280) to Color(0xFF1e3a8a)         // gray-500 → blue-900
        "psp" -> Color(0xFF374151) to Color(0xFF000000)         // gray-700 → black
        "neogeo" -> Color(0xFFeab308) to Color(0xFFb91c1c)      // yellow-500 → red-700
        "pce" -> Color(0xFFf97316) to Color(0xFF991b1b)         // orange-500 → red-800
        "a26" -> Color(0xFFd97706) to Color(0xFF78350f)         // amber-600 → amber-900
        "gg" -> Color(0xFF2563eb) to Color(0xFF1e3a8a)          // blue-600 → blue-900
        "scd" -> Color(0xFF374151) to Color(0xFF1e3a8a)         // gray-700 → blue-900
        "32x" -> Color(0xFF1f2937) to Color(0xFF000000)         // gray-800 → black
        "dc" -> Color(0xFFf97316) to Color(0xFF1d4ed8)          // orange-500 → blue-700
        "vb" -> Color(0xFF991b1b) to Color(0xFF450a0a)          // red-800 → red-950
        "3ds" -> Color(0xFFef4444) to Color(0xFF991b1b)         // red-500 → red-800
        "a52" -> Color(0xFFb45309) to Color(0xFF451a03)         // amber-700 → amber-950
        "a78" -> Color(0xFFd97706) to Color(0xFF78350f)         // amber-600 → amber-900
        "lynx" -> Color(0xFFca8a04) to Color(0xFF713f12)        // yellow-600 → yellow-900
        "jag" -> Color(0xFFb91c1c) to Color(0xFF111827)         // red-700 → gray-900
        "ngp" -> Color(0xFF6b7280) to Color(0xFF1f2937)         // gray-500 → gray-800
        "ws" -> Color(0xFF4f46e5) to Color(0xFF312e81)          // indigo-600 → indigo-900
        "pcfx" -> Color(0xFF0d9488) to Color(0xFF134e4a)        // teal-600 → teal-900
        "cv" -> Color(0xFF0284c7) to Color(0xFF0c4a6e)          // sky-600 → sky-900
        "pkmn" -> Color(0xFFfacc15) to Color(0xFFa16207)        // yellow-400 → yellow-700
        "ps2" -> Color(0xFF1e40af) to Color(0xFF172554)         // blue-800 → blue-950
        "c64" -> Color(0xFF3b82f6) to Color(0xFF6b21a8)         // blue-500 → purple-800
        "dos" -> Color(0xFF15803d) to Color(0xFF052e16)         // green-700 → green-950
        "amiga" -> Color(0xFFdc2626) to Color(0xFF1e40af)       // red-600 → blue-800
        else -> null
    }
    if (gradient != null) return gradient

    // Fallback: use colorTheme hex and darken for the end color
    val baseColor = getConsoleColor(colorTheme)
    return baseColor to baseColor.darken(0.55f)
}

/**
 * An expandable "About" card shown below the hero banner when [ConsoleInfo.summary]
 * is available. Collapsed by default to keep the screen tidy.
 */
@Composable
internal fun ConsoleAboutSection(
    console: Console,
    modifier: Modifier = Modifier,
) {
    val info = getConsoleInfo(console.abbreviation) ?: return
    if (info.summary.isBlank()) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (expanded) info.summary else info.summary,
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (expanded) "Less" else "More",
                style = SpTypography.LabelSmall,
            )
        }
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
