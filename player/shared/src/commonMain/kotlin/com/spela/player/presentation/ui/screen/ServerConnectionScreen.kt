package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import com.spela.player.presentation.ui.TestTags
import com.spela.player.presentation.ui.components.SpActionCard
import com.spela.player.presentation.ui.components.SpAmbientGlowBlobs
import com.spela.player.presentation.ui.theme.spelaBrandGradient
import com.spela.player.presentation.ui.components.SpBrandedBackgroundColor
import com.spela.player.presentation.ui.components.SpGradientBackground
import com.spela.player.presentation.ui.components.SpLogo
import com.spela.player.presentation.viewmodel.ServerConnectionIntent
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel

// SNES button colors used for decorative dots
private val SnesRed = Color(0xFFCC2232)
private val SnesYellow = Color(0xFFC4B208)
private val SnesBlue = Color(0xFF2C2CAA)
private val SnesGreen = Color(0xFF00843D)

@Composable
fun ServerConnectionScreen(
    viewModel: ServerConnectionViewModel,
    onServerSelected: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(ServerConnectionIntent.LoadServers)
    }

    // Auto-expand Add Server form when server list is empty
    LaunchedEffect(state.servers, state.isLoading) {
        println("[ServerConnScreen] LaunchedEffect: isLoading=${state.isLoading}, servers=${state.servers.size}, isAddingServer=${state.isAddingServer}")
        if (!state.isLoading && state.servers.isEmpty() && !state.isAddingServer) {
            println("[ServerConnScreen] → firing ToggleAddServer")
            viewModel.onIntent(ServerConnectionIntent.ToggleAddServer)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag(TestTags.SCREEN_SERVER_CONNECTION)) {
        val layoutMode = when {
            maxWidth > 840.dp -> "desktop"
            maxWidth > 600.dp -> "handheld"
            else -> "mobile"
        }

        when (layoutMode) {
            "mobile" -> MobileLayout(viewModel = viewModel, onServerSelected = onServerSelected)
            else -> {
                val leftFraction = if (layoutMode == "desktop") 0.40f else 0.35f
                SplitLayout(
                    viewModel = viewModel,
                    onServerSelected = onServerSelected,
                    leftFraction = leftFraction,
                )
            }
        }

        // Error snackbar at bottom — always on top of content
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(ServerConnectionIntent.DismissError) },
                    durationMs = 5000L,
                )
            },
            onDismiss = { viewModel.onIntent(ServerConnectionIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ────────────────────────────────────────────────────────
// Mobile layout: full-screen glow background + centered card
// ────────────────────────────────────────────────────────

@Composable
private fun MobileLayout(
    viewModel: ServerConnectionViewModel,
    onServerSelected: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    SpGradientBackground(contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.XLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(size = 192.dp, radius = 16.dp)
            Spacer(Modifier.height(SpSpacing.Small))
            Text(
                text = "\"Nu spelar vi!\"",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.XLarge))

            if (state.isLoading) {
                SpLoadingIndicator(message = "Loading servers...")
            } else {
                ServerListOrForm(
                    viewModel = viewModel,
                    onServerSelected = onServerSelected,
                    fullWidth = true,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────
// Split layout: hero panel + form panel side by side
// ────────────────────────────────────────────────────────

@Composable
private fun SplitLayout(
    viewModel: ServerConnectionViewModel,
    onServerSelected: () -> Unit,
    leftFraction: Float,
) {
    val state by viewModel.state.collectAsState()

    SpGradientBackground {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left hero panel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(leftFraction),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppIcon(size = 240.dp, radius = 20.dp)
                    Spacer(Modifier.height(SpSpacing.Small))
                    Text(
                        text = "\"Nu spelar vi!\"",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundSecondary,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(SpSpacing.XLarge))
                    SnesButtonDots()
                }
            }

            // Right form panel
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.XLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (state.isLoading) {
                        SpLoadingIndicator(message = "Loading servers...")
                    } else {
                        ServerListOrForm(
                            viewModel = viewModel,
                            onServerSelected = onServerSelected,
                            fullWidth = true,
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────
// Shared server list / add-server form
// ────────────────────────────────────────────────────────

@Composable
private fun ServerListOrForm(
    viewModel: ServerConnectionViewModel,
    onServerSelected: () -> Unit,
    fullWidth: Boolean,
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(state.servers) { server ->
            val gradientBrush = spelaBrandGradient()
            SpActionCard(
                onClick = {
                    viewModel.onIntent(ServerConnectionIntent.SelectServer(server.id))
                    onServerSelected()
                },
                onAction = {
                    viewModel.onIntent(ServerConnectionIntent.RemoveServer(server.id))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = server.name },
                action = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove server",
                        tint = SpColor.AccentPurple,
                        modifier = Modifier.size(24.dp),
                    )
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(gradientBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = server.name.take(1).uppercase(),
                        style = SpTypography.TitleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(SpSpacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = SpTypography.TitleLarge,
                        color = SpColor.OnBackground,
                    )
                    Text(
                        text = server.url,
                        style = SpTypography.BodySmall,
                        color = SpColor.AccentPurpleLight,
                    )
                }
                if (server.isActive) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(SpColor.Success),
                    )
                }
            }
        }

        item {
            val animEnabled = com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current
            AnimatedVisibility(
                visible = state.isAddingServer,
                enter = if (animEnabled) expandVertically() + fadeIn() else EnterTransition.None,
                exit = if (animEnabled) shrinkVertically() + fadeOut() else ExitTransition.None,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.servers.isEmpty()) {
                        // No servers yet — show welcoming header inside form area
                        Text(
                            text = "Add Server",
                            style = SpTypography.TitleLarge,
                            color = SpColor.OnBackground,
                        )
                        Spacer(Modifier.height(SpSpacing.XSmall))
                        Text(
                            text = "Enter your server details to connect",
                            style = SpTypography.BodyMedium,
                            color = SpColor.OnBackgroundSecondary,
                        )
                        Spacer(Modifier.height(SpSpacing.Default))
                    }
                    SpTextField(
                        value = state.newServerName,
                        onValueChange = {
                            viewModel.onIntent(ServerConnectionIntent.SetNewServerName(it))
                        },
                        label = "Server Name",
                        placeholder = "My Home Server",
                        modifier = Modifier.testTag(TestTags.SERVER_NAME_INPUT),
                    )
                    Spacer(Modifier.height(SpSpacing.Small))
                    SpTextField(
                        value = state.newServerUrl,
                        onValueChange = {
                            viewModel.onIntent(ServerConnectionIntent.SetNewServerUrl(it))
                        },
                        label = "Server URL",
                        placeholder = "https://spela.example.com",
                        imeAction = ImeAction.Done,
                        onImeAction = { viewModel.onIntent(ServerConnectionIntent.AddServer) },
                        modifier = Modifier.testTag(TestTags.SERVER_URL_INPUT),
                    )
                    Spacer(Modifier.height(SpSpacing.Default))
                    if (state.servers.isEmpty()) {
                        // First server — single prominent "Connect" button
                        SpButton(
                            text = "Connect",
                            onClick = { viewModel.onIntent(ServerConnectionIntent.AddServer) },
                            modifier = Modifier.fillMaxWidth().testTag(TestTags.SERVER_CONNECT_BUTTON),
                            enabled = !state.isValidating,
                            isLoading = state.isValidating,
                        )
                    } else {
                        // Adding an additional server — Cancel + Add
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            SpButton(
                                text = "Cancel",
                                onClick = { viewModel.onIntent(ServerConnectionIntent.ToggleAddServer) },
                                style = SpButtonStyle.Ghost,
                                enabled = !state.isValidating,
                            )
                            Spacer(Modifier.width(SpSpacing.Small))
                            SpButton(
                                text = "Add",
                                onClick = { viewModel.onIntent(ServerConnectionIntent.AddServer) },
                                enabled = !state.isValidating,
                                isLoading = state.isValidating,
                            )
                        }
                    }
                }
            }

            if (!state.isAddingServer) {
                Spacer(Modifier.height(SpSpacing.Default))
                SpSecondaryButton(
                    text = "Add Server",
                    onClick = { viewModel.onIntent(ServerConnectionIntent.ToggleAddServer) },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.SERVER_ADD_TOGGLE_BUTTON),
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────
// Reusable visual sub-components
// ────────────────────────────────────────────────────────

@Composable
private fun AppIcon(size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp) {
    SpLogo(size = size)
}

@Composable
private fun SnesButtonDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(SnesRed, SnesYellow, SnesBlue, SnesGreen).forEach { color ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.6f)),
            )
        }
    }
}

