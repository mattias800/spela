package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Reserves space at the top of a screen for the section indicator pill
 * in gamepad mode. In touch mode this renders nothing — screens should
 * place their own [SpTopBar] before or after this composable.
 *
 * Use this at the top of any screen that manages its own layout
 * (Column, LazyColumn) instead of using SpSectionList.
 * Screens using SpSectionList get this spacing automatically.
 */
@Composable
fun SpScreenTopSpacer() {
    if (LocalInputMode.current == InputMode.GAMEPAD) {
        Spacer(Modifier.height(SpSpacing.SectionIndicatorClearance))
    }
}
