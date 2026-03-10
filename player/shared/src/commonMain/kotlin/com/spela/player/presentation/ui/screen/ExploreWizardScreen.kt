package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.feature.explore.GameShelf
import com.spela.player.presentation.ui.theme.LocalTitleBarInset
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
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
                .padding(top = titleBarInset, start = 8.dp, end = 16.dp),
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
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Decision Wizard",
                color = SpColor.OnBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }

        // Loading state
        if (state.isLoadingSteps) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SpColor.Primary)
            }
            return@Column
        }

        // Progress bar
        if (state.steps.isNotEmpty()) {
            val progress = state.currentStep.toFloat() / state.steps.size.toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = 8.dp)
                    .testTag("wizard_progress"),
                color = SpColor.Primary,
                trackColor = SpColor.SurfaceVariant,
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = step.title,
                            color = SpColor.OnBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            modifier = Modifier.testTag("wizard_step_title"),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(step.options.size) { index ->
                        val option = step.options[index]
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectWizardOption(step.type, option.id)
                                }
                                .testTag("wizard_option_${option.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = SpColor.Surface,
                            shadowElevation = 2.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Text(
                                    text = option.label,
                                    color = SpColor.OnBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                )
                                if (option.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = option.description,
                                        color = SpColor.OnBackgroundSecondary,
                                        fontSize = 13.sp,
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
                                Spacer(modifier = Modifier.width(4.dp))
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = SpColor.Primary)
                }
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
                        Spacer(modifier = Modifier.height(16.dp))
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
                                .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.resultTitle.ifEmpty { "Your Perfect Picks" },
                                color = SpColor.OnBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
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
