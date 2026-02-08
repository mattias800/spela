package com.spela.player.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.spela.player.presentation.viewmodel.LoginViewModel
import org.koin.compose.koinInject

/**
 * Root composable for the Spela player app.
 * Handles navigation between Login, Dashboard, Game Detail, and Emulation screens.
 */
@Composable
fun App() {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF6366F1),       /* Indigo */
        secondary = Color(0xFF8B5CF6),     /* Purple */
        tertiary = Color(0xFFEC4899),      /* Pink */
        background = Color(0xFF0F0F23),    /* Deep dark */
        surface = Color(0xFF1A1A2E),       /* Card dark */
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
    )

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val loginViewModel: LoginViewModel = koinInject()
            val loginState by loginViewModel.state.collectAsState()

            if (loginState.isLoggedIn) {
                /* Main app navigation - placeholder for UI dev to implement */
                MainContent()
            } else {
                /* Login screen - placeholder for UI dev to implement */
                LoginPlaceholder(loginViewModel)
            }
        }
    }
}

@Composable
private fun LoginPlaceholder(viewModel: LoginViewModel) {
    /* The player-app-ui agent will replace this with a real login screen */
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = "Spela - Login",
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
private fun MainContent() {
    /* The player-app-ui agent will replace this with real navigation */
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = "Spela - Dashboard",
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}
