package com.spela.player.presentation.ui.feature.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpGradientBackground
import com.spela.player.presentation.ui.components.SpLogo
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/** Content column max width so the wizard reads as a focused card on tablets,
 *  desktop, and the side-by-side handheld landscape — not a stretched form. */
private val WizardContentMaxWidth = 460.dp

/**
 * The shared chrome for every first-run wizard step (#1448): the brand gradient
 * background, the Spela logo + tagline, a step-progress indicator, a centered
 * title/subtitle, the per-step body (passed as a [content] slot — the
 * render-prop seam that lets each step compose its own fields + primary action
 * without this shell knowing anything about them), and an optional Back.
 *
 * Layout mirrors the auth screens it sits between: a single centered column in
 * portrait, and a side-by-side hero panel + form panel in landscape (the AYN
 * Thor / Steam Deck primary target, and desktop). Steps stay dumb about layout;
 * this owns it. Adding a step is: add an
 * [com.spela.player.presentation.viewmodel.OnboardingStep] entry and a content
 * composable — the chrome and progress dots scale automatically.
 */
@Composable
fun OnboardingWizardChrome(
    stepIndex: Int,
    stepCount: Int,
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    SpGradientBackground {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                // Landscape: hero panel (branding + progress) beside the form,
                // matching SplitLoginLayout.
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(0.40f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            WizardBrand(logoSize = 200.dp)
                            Spacer(Modifier.height(SpSpacing.XLarge))
                            WizardProgress(stepIndex = stepIndex, stepCount = stepCount)
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = WizardContentMaxWidth)
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .imePadding()
                                .padding(horizontal = SpSpacing.XLarge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            WizardForm(title, subtitle, onBack, content)
                        }
                    }
                }
            } else {
                // Portrait: single centered column, branding + progress on top.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = WizardContentMaxWidth)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .imePadding()
                            .padding(horizontal = SpSpacing.ScreenHorizontal),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(SpSpacing.Large))
                        WizardBrand(logoSize = 132.dp)
                        Spacer(Modifier.height(SpSpacing.Large))
                        WizardProgress(stepIndex = stepIndex, stepCount = stepCount)
                        Spacer(Modifier.height(SpSpacing.XLarge))
                        WizardForm(title, subtitle, onBack, content)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.WizardForm(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = SpTypography.HeadlineMedium,
        color = SpColor.OnBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (subtitle != null) {
        Spacer(Modifier.height(SpSpacing.Small))
        Text(
            text = subtitle,
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(SpSpacing.XLarge))
    content()
    if (onBack != null) {
        Spacer(Modifier.height(SpSpacing.Medium))
        SpButton(text = "Back", onClick = onBack, style = SpButtonStyle.Ghost)
    }
    Spacer(Modifier.height(SpSpacing.XLarge))
}

@Composable
private fun WizardBrand(logoSize: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SpLogo(size = logoSize)
        Spacer(Modifier.height(SpSpacing.Small))
        Text(
            text = "\"Nu spelar vi!\"",
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
        )
    }
}

/** Pill-style step indicator: the active step is an elongated accent pill, the
 *  rest are small dim dots. Scales to any [stepCount]. */
@Composable
private fun WizardProgress(stepIndex: Int, stepCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
        repeat(stepCount) { i ->
            val active = i == stepIndex
            val target = if (active) 24.dp else SpSpacing.Small
            // Bypass the width animation in test mode (LocalAnimationsEnabled=false):
            // an in-flight transition leaves a coroutine active when runComposeUiTest
            // tears down → UncompletedCoroutinesError. Matches the SpelaApp pattern.
            val width = if (com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current) {
                animateDpAsState(target, label = "wizardDotWidth").value
            } else {
                target
            }
            Box(
                modifier = Modifier
                    .size(width = width, height = SpSpacing.Small)
                    .clip(if (active) RoundedCornerShape(SpSpacing.RadiusSmall) else CircleShape)
                    .background(if (active) SpColor.OnBackground else SpColor.OnBackgroundTertiary),
            )
        }
    }
}
