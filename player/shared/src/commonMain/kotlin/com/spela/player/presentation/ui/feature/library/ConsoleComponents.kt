package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    modifier: Modifier = Modifier,
) {
    val consoleColor = getConsoleColor(console.colorTheme)

    SpGradientCard(
        modifier = modifier
            .height(100.dp)
            .semantics {
                contentDescription = "${console.name}, ${console.gameCount} games"
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
                Text(
                    text = console.name,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnBackground,
                )
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
