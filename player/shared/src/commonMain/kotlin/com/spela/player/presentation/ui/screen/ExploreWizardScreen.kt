package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpProgressBar
import com.spela.player.presentation.ui.feature.explore.GameShelf
import com.spela.player.presentation.ui.feature.explore.GameShelfSkeleton
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel

@Composable
fun ExploreWizardScreen(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.wizardState.collectAsState()
    val titleBarInset = LocalTitleBarInset.current

    LaunchedEffect(Unit) {
        viewModel.loadWizardSteps()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("wizard_screen"),
    ) {
        // Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = titleBarInset, start = SpSpacing.Small, end = SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = SpColor.OnBackground,
                )
            }
            Icon(
                imageVector = Icons.Filled.AutoFixHigh,
                contentDescription = null,
                tint = SpColor.Primary,
            )
            Spacer(modifier = Modifier.width(SpSpacing.Small))
            Text(
                text = "Decision Wizard",
                color = SpColor.OnBackground,
                style = SpTypography.HeadlineMedium,
            )
        }

        // Loading state
        if (state.isLoadingSteps) {
            // Loading skeleton for steps
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Default),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                // Title skeleton
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(SpSpacing.XLarge)
                        .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
                        .background(SpColor.SurfaceVariant),
                )
                // Option card skeletons
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
                            .background(SpColor.SurfaceVariant),
                    )
                }
            }
            return@Column
        }

        // Error state
        if (state.error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SpEmptyState(
                    icon = Icons.Filled.AutoFixHigh,
                    title = "Something went wrong",
                    message = state.error ?: "Failed to load wizard. Please try again.",
                    modifier = Modifier.testTag("wizard_error_state"),
                )
            }
            return@Column
        }

        // Progress bar
        if (state.steps.isNotEmpty()) {
            val progress = state.currentStep.toFloat() / state.steps.size.toFloat()
            SpProgressBar(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small)
                    .testTag("wizard_progress"),
            )
        }

        if (!state.isComplete) {
            // Show current step
            val step = state.currentStepData
            if (step != null) {
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
                            text = step.title,
                            color = SpColor.OnBackground,
                            style = SpTypography.HeadlineLarge,
                            modifier = Modifier.testTag("wizard_step_title"),
                        )
                        Spacer(modifier = Modifier.height(SpSpacing.Small))
                    }

                    items(step.options.size) { index ->
                        val option = step.options[index]
                        SpCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wizard_option_${option.id}"),
                            onClick = { viewModel.selectWizardOption(step.type, option.id) },
                        ) {
                            Column(
                                modifier = Modifier.padding(SpSpacing.Default),
                            ) {
                                Text(
                                    text = option.label,
                                    color = SpColor.OnBackground,
                                    style = SpTypography.TitleLarge,
                                )
                                if (option.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(SpSpacing.XSmall))
                                    Text(
                                        text = option.description,
                                        color = SpColor.OnBackgroundSecondary,
                                        style = SpTypography.BodySmall,
                                    )
                                }
                            }
                        }
                    }

                    // Back button
                    if (state.currentStep > 0) {
                        item {
                            TextButton(
                                onClick = { viewModel.wizardGoBack() },
                                modifier = Modifier.testTag("wizard_back_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = SpColor.OnBackgroundSecondary,
                                )
                                Spacer(modifier = Modifier.width(SpSpacing.XSmall))
                                Text(
                                    text = "Back",
                                    color = SpColor.OnBackgroundSecondary,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Results
            if (state.isLoadingResults) {
                GameShelfSkeleton()
            } else if (state.resultGames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SpEmptyState(
                            icon = Icons.Filled.AutoFixHigh,
                            title = "No games found",
                            message = "No games match your preferences. Try different choices!",
                            modifier = Modifier.testTag("wizard_empty_state"),
                        )
                        Spacer(modifier = Modifier.height(SpSpacing.Default))
                        TextButton(onClick = { viewModel.restartWizard() }) {
                            Text("Try Again", color = SpColor.Primary)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = SpSpacing.XLarge,
                    ),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.resultTitle.ifEmpty { "Your Perfect Picks" },
                                color = SpColor.OnBackground,
                                style = SpTypography.HeadlineMedium,
                                modifier = Modifier.testTag("wizard_results_title"),
                            )
                            Row {
                                IconButton(onClick = { viewModel.shuffleWizardResults() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = "Shuffle",
                                        tint = SpColor.OnBackgroundSecondary,
                                    )
                                }
                                TextButton(onClick = { viewModel.restartWizard() }) {
                                    Text("Start Over", color = SpColor.Primary)
                                }
                            }
                        }
                    }

                    item {
                        GameShelf(
                            games = state.resultGames,
                            onGameSelected = onGameSelected,
                            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                        )
                    }
                }
            }
        }
    }
}
