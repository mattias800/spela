package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private data class CreditEntry(
    val name: String,
    val url: String,
    val license: String,
    val description: String,
)

private val credits = listOf(
    CreditEntry(
        name = "retro-game-console-icons",
        url = "github.com/KyleBing/retro-game-console-icons",
        license = "GPL-3.0",
        description = "Console hardware icons used in the library view.",
    ),
    CreditEntry(
        name = "console-logos",
        url = "github.com/PRO100BYTE/console-logos",
        license = "Free to use",
        description = "Console logo SVGs by Dan Patrick used in console detail pages.",
    ),
    CreditEntry(
        name = "Console-Iconset",
        url = "github.com/Tatohead/Console-Iconset",
        license = "Free to use",
        description = "Pixel art console and controller icons by Tatohead.",
    ),
    CreditEntry(
        name = "The Noun Project",
        url = "thenounproject.com",
        license = "CC BY 3.0",
        description = "Atari Jaguar controller icon used in the library view.",
    ),
    CreditEntry(
        name = "Controllers Stencil Platform Images",
        url = "forums.launchbox-app.com/files/file/3480-controllers-stencil-platform-images/",
        license = "Free to use",
        description = "White stencil controller icons by EthanAllen used in the library view.",
    ),
    CreditEntry(
        name = "Icons8",
        url = "icons8.com",
        license = "Free with attribution",
        description = "GameCube and 3DS console icons used in the library view.",
    ),
    CreditEntry(
        name = "libretro / RetroArch",
        url = "www.libretro.com",
        license = "GPL-3.0",
        description = "Emulation API and cores used by the player app.",
    ),
    CreditEntry(
        name = "RetroAchievements",
        url = "retroachievements.org",
        license = "Community project",
        description = "Achievement system for retro games.",
    ),
    CreditEntry(
        name = "Coil",
        url = "github.com/coil-kt/coil",
        license = "Apache-2.0",
        description = "Image loading library for Compose Multiplatform.",
    ),
)

@Composable
fun LicensesScreen(
    onBack: () -> Unit = {},
) {
    PlatformBackHandler { onBack() }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    SpScreen {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(title = "Credits & Licenses", showBack = true, onBack = onBack)
            }

            val focusMemory = rememberFocusMemoryState()
            CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
            LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = SpSpacing.ScreenHorizontal,
                vertical = SpSpacing.Default,
            ),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            item {
                Text(
                    text = "Spela uses the following open-source projects and resources.",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                )
            }

            items(credits) { entry ->
                SpCard(
                    modifier = (if (entry == credits.firstOrNull()) Modifier.autoFocus() else Modifier)
                        .rememberFocus(entry.name),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SpSpacing.Default),
                    ) {
                        Text(
                            text = entry.name,
                            style = SpTypography.TitleLarge,
                            color = SpColor.OnCard,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(SpSpacing.XSmall))
                        Text(
                            text = entry.description,
                            style = SpTypography.BodyMedium,
                            color = SpColor.OnBackgroundSecondary,
                        )
                        Spacer(Modifier.height(SpSpacing.XSmall))
                        Text(
                            text = "License: ${entry.license}",
                            style = SpTypography.BodySmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                        Spacer(Modifier.height(SpSpacing.XXSmall))
                        Text(
                            text = entry.url,
                            style = SpTypography.BodySmall,
                            color = SpColor.Accent,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(SpSpacing.XXXLarge))
            }
        }
        } // CompositionLocalProvider
        }
    }
}
