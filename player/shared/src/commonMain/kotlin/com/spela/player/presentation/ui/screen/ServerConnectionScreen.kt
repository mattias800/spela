package com.spela.player.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ServerConnectionIntent
import com.spela.player.presentation.viewmodel.ServerConnectionViewModel

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
        if (!state.isLoading && state.servers.isEmpty() && !state.isAddingServer) {
            viewModel.onIntent(ServerConnectionIntent.ToggleAddServer)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        val isLandscape = maxWidth > maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SpSpacing.ScreenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (isLandscape) SpSpacing.XLarge else 80.dp))

            // App branding
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 56.dp else 80.dp)
                    .clip(RoundedCornerShape(if (isLandscape) 14.dp else 20.dp))
                    .background(SpColor.PrimaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "S",
                    style = if (isLandscape) SpTypography.TitleLarge else SpTypography.DisplayMedium,
                    color = SpColor.Primary,
                )
            }

            Spacer(Modifier.height(SpSpacing.Default))

            Text(
                text = "Spela",
                style = SpTypography.DisplaySmall,
                color = SpColor.OnBackground,
            )

            Text(
                text = "Connect to your game server",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )

            Spacer(Modifier.height(if (isLandscape) SpSpacing.XLarge else SpSpacing.XXXLarge))

            if (state.isLoading) {
                SpLoadingIndicator(message = "Loading servers...")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .then(if (isLandscape) Modifier.widthIn(max = 550.dp) else Modifier.fillMaxWidth()),
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                ) {
                    items(state.servers) { server ->
                        SpCard(
                            onClick = {
                                viewModel.onIntent(ServerConnectionIntent.SelectServer(server.id))
                                onServerSelected()
                            },
                            onGradient = true,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = server.name }
                                    .padding(SpSpacing.Default),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SpColor.PrimaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = server.name.take(1).uppercase(),
                                        style = SpTypography.TitleLarge,
                                        color = SpColor.Primary,
                                    )
                                }
                                Spacer(Modifier.width(SpSpacing.Medium))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = server.name,
                                        style = SpTypography.TitleLarge,
                                        color = SpColor.OnCard,
                                    )
                                    Text(
                                        text = server.url,
                                        style = SpTypography.BodySmall,
                                        color = SpColor.OnBackgroundTertiary,
                                    )
                                }
                                if (server.isActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(SpColor.Success),
                                    )
                                    Spacer(Modifier.width(SpSpacing.Medium))
                                }
                                // Delete button
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Remove server",
                                    tint = SpColor.OnBackgroundTertiary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = {
                                            viewModel.onIntent(ServerConnectionIntent.RemoveServer(server.id))
                                        })
                                        .padding(SpSpacing.Small),
                                )
                            }
                        }
                    }

                    item {
                        // Add Server section
                        AnimatedVisibility(
                            visible = state.isAddingServer,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SpCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(SpSpacing.Default),
                                ) {
                                    Text(
                                        text = "Add Server",
                                        style = SpTypography.TitleLarge,
                                        color = SpColor.OnCard,
                                    )
                                    Spacer(Modifier.height(SpSpacing.Medium))
                                    SpTextField(
                                        value = state.newServerName,
                                        onValueChange = {
                                            viewModel.onIntent(ServerConnectionIntent.SetNewServerName(it))
                                        },
                                        label = "Server Name",
                                        placeholder = "My Home Server",
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
                                    )
                                    Spacer(Modifier.height(SpSpacing.Default))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        SpButton(
                                            text = "Cancel",
                                            onClick = { viewModel.onIntent(ServerConnectionIntent.ToggleAddServer) },
                                            style = SpButtonStyle.Ghost,
                                        )
                                        Spacer(Modifier.width(SpSpacing.Small))
                                        SpButton(
                                            text = "Add",
                                            onClick = { viewModel.onIntent(ServerConnectionIntent.AddServer) },
                                        )
                                    }
                                }
                            }
                        }

                        if (!state.isAddingServer) {
                            Spacer(Modifier.height(SpSpacing.Default))
                            SpButton(
                                text = "Add Server",
                                onClick = { viewModel.onIntent(ServerConnectionIntent.ToggleAddServer) },
                                style = SpButtonStyle.Outlined,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

        }

        // Error snackbar with auto-dismiss
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
