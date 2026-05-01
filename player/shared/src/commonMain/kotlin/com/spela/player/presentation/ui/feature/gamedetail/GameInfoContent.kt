package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.BiosMissingFile
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.presentation.state.GameDetailState
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Middle column of the game-detail layout — description, metadata
 * grid, variants, based-on ROM hack, and standalone ROM hacks
 * section. Extracted from `GameDetailScreen` in the #695 refactor.
 *
 * Title, badges, action buttons, and loading indicators render in
 * the hero banner via [GameHeroContent], not here.
 */
@Composable
fun GameInfoContent(
    gameId: String,
    game: Game,
    detail: GameDetail,
    state: GameDetailState,
    isPortrait: Boolean = false,
    hasSaves: Boolean,
    missingBiosFiles: List<BiosMissingFile> = emptyList(),
    isDemoConsole: Boolean = false,
    onPlay: (String) -> Unit,
    onPlayFresh: ((String) -> Unit)? = null,
    onPlayFromTitleScreen: ((String) -> Unit)? = null,
    onDownloadGame: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayLater: () -> Unit,
    onAddToCollection: () -> Unit,
    onCreateNetplay: ((String) -> Unit)? = null,
    onDeleteLocalGame: () -> Unit = {},
    onRate: (Int) -> Unit = {},
    syncState: com.spela.player.presentation.state.GameSyncState? = null,
    onPlayWithLocalSave: () -> Unit = {},
    onCancelLaunch: () -> Unit = {},
    onNavigateToGame: ((String) -> Unit)? = null,
    onNavigateToDeveloper: ((name: String) -> Unit)? = null,
    onNavigateToPublisher: ((name: String) -> Unit)? = null,
    onNavigateToAchievements: (() -> Unit)? = null,
) {
    // BIOS warning chip
    if (missingBiosFiles.isNotEmpty()) {
        var showBiosInfo by remember { mutableStateOf(false) }

        BiosWarningChip(
            missingFiles = missingBiosFiles,
            onClick = { showBiosInfo = !showBiosInfo },
        )
        AnimatedVisibility(visible = showBiosInfo) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SpSpacing.Small)
                    .background(
                        SpColor.Warning.copy(alpha = 0.1f),
                        RoundedCornerShape(SpSpacing.RadiusMedium),
                    )
                    .padding(SpSpacing.Medium)
                    .semantics { contentDescription = "Missing BIOS files info" },
            ) {
                Text(
                    text = "Missing BIOS files:",
                    style = SpTypography.LabelMedium,
                    color = SpColor.Warning,
                )
                missingBiosFiles.forEach { file ->
                    Text(
                        text = file.fileName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundSecondary,
                    )
                }
            }
        }
    }

    // Description (plain text, matching web UI)
    game.description?.let { description ->
        Text(
            text = description,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )
    }

    // Metadata grid (Developer, Publisher, Released, Genre, Players, Achievements, Size, Discs)
    MetadataGrid(
        game = game,
        onGradient = true,
        isDemoConsole = isDemoConsole,
        achievementTotal = state.achievements.size,
        achievementUnlocked = state.achievementProgress.size,
        onDeveloperClick = onNavigateToDeveloper,
        onPublisherClick = onNavigateToPublisher,
        onAchievementsClick = onNavigateToAchievements,
    )

    // Variants section -- split into Versions (non-hack) and ROM Hacks (hack-tagged)
    val versionVariants = detail.variants.filter { variant ->
        variant.tags?.split(",")?.map { it.trim().lowercase() }?.contains("hack") != true
    }
    val hackVariants = detail.variants.filter { variant ->
        variant.tags?.split(",")?.map { it.trim().lowercase() }?.contains("hack") == true
    }

    if (versionVariants.isNotEmpty()) {
        Spacer(Modifier.height(SpSpacing.Default))
        VariantsSection(
            title = "Versions",
            variants = versionVariants,
            onVariantSelected = onNavigateToGame,
        )
    }

    if (hackVariants.isNotEmpty()) {
        Spacer(Modifier.height(SpSpacing.Default))
        VariantsSection(
            title = "ROM Hacks",
            variants = hackVariants,
            onVariantSelected = onNavigateToGame,
        )
    }

    // "Based on" section for standalone ROM hacks
    detail.parentGame?.let { parent ->
        Spacer(Modifier.height(SpSpacing.Default))
        BasedOnSection(
            parentGame = parent,
            onNavigateToGame = onNavigateToGame,
        )
    }

    // Standalone ROM Hacks section
    if (detail.romHacks.isNotEmpty()) {
        Spacer(Modifier.height(SpSpacing.Default))
        RomHacksSection(
            romHacks = detail.romHacks,
            onNavigateToGame = onNavigateToGame,
        )
    }

    // The per-game Save-states toggle that used to live here was moved
    // into the hero action row's gear icon → Game settings sheet (#855).
    // The info column is now exclusively facts about the game; mutable
    // per-game policy is one tap away in the Game settings sheet.
}
