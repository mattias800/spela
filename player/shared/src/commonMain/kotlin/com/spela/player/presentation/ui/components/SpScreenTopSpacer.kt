package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * The section-indicator pill clearance for gamepad mode, sized to clear the
 * floating pill. Add this to a lazy scroller's `contentPadding` top (LazyColumn /
 * LazyVerticalGrid / [SpLazyVerticalGrid]) so its content scrolls *under* the
 * floating pill.
 *
 * Prefer this over placing [SpScreenTopSpacer] as a fixed sibling above a lazy
 * scroller: a fixed spacer is outside the scroll region, so content clips at an
 * opaque seam below the pill instead of scrolling under it. Returns `0.dp` in
 * touch mode.
 */
@Composable
fun sectionPillClearance(): Dp =
    if (LocalInputMode.current == InputMode.GAMEPAD) SpSpacing.SectionIndicatorClearance else 0.dp
