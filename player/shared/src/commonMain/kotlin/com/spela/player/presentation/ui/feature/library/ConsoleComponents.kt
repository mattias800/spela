package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.rememberFocus
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.spela.player.domain.model.Console
import com.spela.player.presentation.ui.components.SpAreaSizedImage
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.gamepad.autoFocus
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

// Named alpha constants for elements rendered over coloured hero banner backgrounds.
private val HeroBorder         = Color.White.copy(alpha = 0.06f)
private val HeroOverlayTop     = Color.White.copy(alpha = 0.04f)
private val HeroOverlayBottom  = Color.Black.copy(alpha = 0.30f)
private val HeroTextPrimary    = Color.White.copy(alpha = 0.90f)
private val HeroTextSecondary  = Color.White.copy(alpha = 0.70f)
private val HeroBadgeBackground = Color.White.copy(alpha = 0.10f)

// #1082: Per-card width cap. With the fixed 120 dp card height, anything
// past ~340 dp degrades the design's intended 1.9:1 aspect ratio into a
// thin strip. Keeps the card content (140 dp watermark icon, 0.65-width
// logo, bottom-left metadata) coherent on all viewports — same visual
// weight at 1920 dp and at 700 dp tablets, just more cards per row.
internal val ConsoleCardMaxWidth = 340.dp

/**
 * #1082: Column count for the consoles grid given the available container
 * width. Extracted so the breakpoint logic is unit-testable without
 * spinning up Compose UI.
 *
 * Pairs with [ConsoleCardMaxWidth] — the breakpoints aim to keep density
 * up at standard desktop widths (1100+ → 4 cols, 1500+ → 5 cols), and
 * the per-card width cap then stops cards from stretching past the
 * design's intended ~340 dp width when the row's available width
 * exceeds `cols × 340 dp`.
 */
internal fun consoleColumnsForWidth(width: androidx.compose.ui.unit.Dp): Int = when {
    width >= 1500.dp -> 5
    width >= 1100.dp -> 4
    width >= 700.dp  -> 3
    width >= 400.dp  -> 2
    else             -> 1
}

private fun generationLabel(generation: Int): String = when (generation) {
    2 -> "2nd Generation · 1976–1992"
    3 -> "3rd Generation · 1983–1992"
    4 -> "4th Generation · 1987–1996"
    5 -> "5th Generation · 1993–2006"
    6 -> "6th Generation · 1998–2007"
    7 -> "7th Generation · 2004–2013"
    8 -> "8th Generation · 2011–2020"
    9 -> "9th Generation · 2017–present"
    100 -> "Home Computers · 1977–1995"
    101 -> "Arcade · 1971–present"
    else -> "Other"
}

@Composable
internal fun ConsolesGrid(
    consoles: List<Console>,
    onConsoleSelected: (String) -> Unit,
    consolesWithMissingBios: Set<String> = emptySet(),
    columnsPerRow: Int = 2,
) {
    val grouped = consoles.groupBy { it.generation }.toSortedMap()
    val focusMemory = rememberFocusMemoryState()

    var isFirstCard = true

    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        grouped.entries.forEachIndexed { index, (generation, groupConsoles) ->
            if (index > 0) {
                Spacer(Modifier.height(SpSpacing.Medium))
            }
            Text(
                text = generationLabel(generation),
                style = SpTypography.TitleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
            )
            groupConsoles.chunked(columnsPerRow).forEach { rowConsoles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                ) {
                    rowConsoles.forEach { console ->
                        // #1082: weight allocates equal share, then widthIn
                        // clamps the result so cards never stretch past their
                        // intended visual weight on wide windows. Beyond the
                        // cap, leftover row width distributes via the existing
                        // weight(1f) Spacer logic below for partial last rows.
                        val cardModifier = if (isFirstCard) {
                            isFirstCard = false
                            Modifier
                                .weight(1f)
                                .widthIn(max = ConsoleCardMaxWidth)
                                .autoFocus()
                                .rememberFocus(console.id)
                        } else {
                            Modifier
                                .weight(1f)
                                .widthIn(max = ConsoleCardMaxWidth)
                                .rememberFocus(console.id)
                        }
                        ConsoleCard(
                            console = console,
                            onClick = { onConsoleSelected(console.id) },
                            hasMissingBios = console.id in consolesWithMissingBios,
                            modifier = cardModifier,
                        )
                    }
                    repeat(columnsPerRow - rowConsoles.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    } // CompositionLocalProvider
}

@Composable
internal fun ConsoleCard(
    console: Console,
    onClick: () -> Unit,
    hasMissingBios: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (gradientFrom, gradientTo) = getConsoleGradient(console.abbreviation, console.colorTheme)
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)
    val biosDesc = if (hasMissingBios) ", BIOS missing" else ""
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.97f
            isFocused -> 1.03f
            isHovered -> 1.02f
            else -> 1f
        },
        animationSpec = tween(150),
        label = "consoleCardScale",
    )

    Box(
        modifier = modifier
            .testTag(com.spela.player.presentation.ui.TestTags.consoleCard(console.id))
            .height(120.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val d = (size.width + size.height) * 0.25f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(gradientFrom, gradientTo),
                        start = Offset(cx - d, cy - d),
                        end = Offset(cx + d, cy + d),
                    ),
                )
            }
            .border(1.dp, HeroBorder, shape)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .gamepadFocusable(shape = shape, interactionSource = interactionSource)
            .semantics {
                contentDescription = "${console.name}, ${console.gameCount} games$biosDesc"
                role = Role.Button
            },
    ) {
        // Watermark icon: bottom-end, clipped, 7% opacity
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 20.dp, y = 20.dp)
                .size(140.dp)
                .alpha(0.07f),
        ) {
            if (console.iconUrl.isNotEmpty()) {
                AsyncImage(
                    model = console.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Vertical depth overlay: light top, dark bottom
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(HeroOverlayTop, Color.Transparent, HeroOverlayBottom),
                    ),
                ),
        )

        // Centered logo with dual constraints (width + height)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.65f)
                .heightIn(max = 64.dp),
            contentAlignment = Alignment.Center,
        ) {
            var logoFailed by remember { mutableStateOf(false) }
            if (console.logoUrl.isNotEmpty() && !logoFailed) {
                AsyncImage(
                    model = console.logoUrl,
                    contentDescription = console.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 64.dp),
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                )
            } else {
                Text(
                    text = console.name,
                    style = SpTypography.HeadlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HeroTextPrimary,
                )
            }
        }

        // BIOS warning icon at top-right corner
        if (hasMissingBios) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "BIOS missing for ${console.name}",
                tint = SpColor.Warning,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(SpSpacing.Small)
                    .size(16.dp),
            )
        }

        // Bottom-left: game count · manufacturer · year
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(SpSpacing.Small),
        ) {
            val info = buildString {
                append("${console.gameCount} ${if (console.gameCount == 1) "game" else "games"}")
                if (console.makerName != null) {
                    append(" · ${console.makerName}")
                }
                if (console.releaseYear != null) {
                    append(" · ${console.releaseYear}")
                }
            }
            Text(
                text = info,
                style = SpTypography.LabelSmall,
                color = HeroTextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ConsoleCardSkeleton(
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(shape)
            .background(SpColor.SurfaceVariant),
    ) {
        // Centered logo placeholder
        Box(
            modifier = Modifier.align(Alignment.Center),
        ) {
            SpShimmer(width = 120.dp, height = 28.dp)
        }
        // Bottom-left stats placeholder
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(SpSpacing.Default),
        ) {
            SpShimmer(width = 64.dp, height = 12.dp)
            Spacer(Modifier.height(SpSpacing.XSmall))
            SpShimmer(width = 96.dp, height = 10.dp)
        }
    }
}

@Composable
internal fun ConsolesSkeletonGrid(
    columnsPerRow: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        (0 until count).chunked(columnsPerRow).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                row.forEach { _ ->
                    ConsoleCardSkeleton(modifier = Modifier.weight(1f))
                }
                repeat(columnsPerRow - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun ConsoleHeroBanner(
    console: Console,
    modifier: Modifier = Modifier,
    onBrowseGames: (() -> Unit)? = null,
    onConsoleSettings: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val showButtonsBelow = maxWidth < 700.dp
        Column {
            ConsoleHeroBannerContent(
                console = console,
                onBrowseGames = onBrowseGames,
                onConsoleSettings = onConsoleSettings,
                showButtons = !showButtonsBelow,
            )
            if (showButtonsBelow && (onBrowseGames != null || onConsoleSettings != null)) {
                Spacer(Modifier.height(SpSpacing.Medium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    if (onConsoleSettings != null) {
                        SpButton(
                            text = "Settings",
                            onClick = onConsoleSettings,
                            style = SpButtonStyle.Outlined,
                            modifier = Modifier.weight(1f).autoFocus(),
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                    if (onBrowseGames != null) {
                        SpButton(
                            text = "Browse",
                            onClick = onBrowseGames,
                            style = SpButtonStyle.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleHeroBannerContent(
    console: Console,
    onBrowseGames: (() -> Unit)? = null,
    onConsoleSettings: (() -> Unit)? = null,
    showButtons: Boolean = true,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)

    // Per-console gradient matching web UI's console-metadata.ts exactly
    val (gradientFrom, gradientTo) = getConsoleGradient(console.abbreviation, console.colorTheme)
    val gradientColors = listOf(gradientFrom, gradientTo)

    // Depth overlay matching web: from-black/30 via-transparent to-white/[0.04]
    // Web uses bg-gradient-to-t (bottom→top), so in vertical gradient: top=white/4, mid=transparent, bottom=black/30
    val overlayBrush = Brush.verticalGradient(
        colors = listOf(
            HeroOverlayTop,
            Color.Transparent,
            HeroOverlayBottom,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .focusGroup()
            .clip(shape)
            // CSS 135deg-equivalent gradient: fixed 45° diagonal (top-left → bottom-right)
            // regardless of aspect ratio, matching the visual appearance on the web.
            // d = (w + h) / 4 — the displacement from centre along both axes.
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val d = (size.width + size.height) * 0.25f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset(cx - d, cy - d),
                        end = Offset(cx + d, cy + d),
                    ),
                )
            }
            .border(1.dp, HeroBorder, shape)
            .semantics {
                contentDescription = "${console.name}, ${console.gameCount} games"
            },
    ) {
        // Watermark icon: web uses -right-8 -top-8 (32px) at 224px (h-56 w-56), opacity 7%
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 32.dp, y = (-32).dp)
                .alpha(0.07f),
        ) {
            if (console.iconUrl.isNotEmpty()) {
                AsyncImage(
                    model = console.iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(224.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(224.dp),
                )
            }
        }

        // Gradient overlay for depth
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(overlayBrush),
        )

        // Content: responsive layout
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SpSpacing.XLarge, vertical = SpSpacing.Medium),
        ) {
            val isWide = maxWidth > 500.dp

            // Logo or text fallback (shared)
            // Scale logo dynamically with banner width — wider banners get bigger logos
            val widthDp = maxWidth
            val logoArea = (widthDp.value * 22f).coerceIn(8000f, 30000f)
            val logoMaxH = (widthDp.value * 0.14f).coerceIn(60f, 140f).dp
            val logoMaxW = (widthDp.value * 0.4f).coerceIn(160f, 400f).dp

            var logoFailed by remember { mutableStateOf(false) }
            val logoContent: @Composable () -> Unit = {
                if (console.logoUrl.isNotEmpty() && !logoFailed) {
                    SpAreaSizedImage(
                        imageUrl = console.logoUrl,
                        contentDescription = console.name,
                        targetArea = logoArea,
                        maxHeight = logoMaxH,
                        maxWidth = logoMaxW,
                        minHeight = 40.dp,
                        error = {
                            logoFailed = true
                            Text(
                                text = console.name,
                                style = if (isWide) SpTypography.HeadlineLarge else SpTypography.HeadlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        },
                    )
                }
                if (console.logoUrl.isEmpty() || logoFailed) {
                    Text(
                        text = console.name,
                        style = if (isWide) SpTypography.HeadlineLarge else SpTypography.HeadlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            // Metadata badges (shared)
            val badgesContent: @Composable () -> Unit = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${console.gameCount} ${if (console.gameCount == 1) "game" else "games"}",
                        style = SpTypography.BodySmall,
                        fontWeight = FontWeight.Medium,
                        color = HeroTextSecondary,
                    )
                    if (console.saveStateSupport) {
                        MetadataBadge(
                            icon = { Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = HeroTextPrimary) },
                            label = "Save states",
                        )
                    }
                    if (console.browserPlayable) {
                        MetadataBadge(
                            icon = { Icon(Icons.Filled.Language, null, Modifier.size(12.dp), tint = HeroTextPrimary) },
                            label = "Browser play",
                        )
                    }
                }
            }

            // Data table left, logo centered, feature info right, browse button bottom-right
            Box(modifier = Modifier.fillMaxSize()) {
                // Left: console stats
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .widthIn(max = 96.dp),
                ) {
                    ConsoleInfoSection(console = console)
                }

                // Center: logo + game count badge
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    logoContent()
                    Spacer(modifier = Modifier.height(SpSpacing.Small))
                    MetadataBadge(
                        icon = { Icon(Icons.Filled.SportsEsports, null, Modifier.size(12.dp), tint = HeroTextPrimary) },
                        label = "${console.gameCount} ${if (console.gameCount == 1) "game" else "games"}",
                    )
                }

                // Top-right: feature badges
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    if (console.saveStateSupport) {
                        MetadataBadge(
                            icon = { Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = HeroTextPrimary) },
                            label = "Save states",
                        )
                    }
                    if (console.browserPlayable) {
                        MetadataBadge(
                            icon = { Icon(Icons.Filled.Language, null, Modifier.size(12.dp), tint = HeroTextPrimary) },
                            label = "Browser play",
                        )
                    }
                }

                // Bottom-right: action buttons (only when parent says so)
                if (showButtons && (onBrowseGames != null || onConsoleSettings != null)) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                        horizontalAlignment = Alignment.End,
                    ) {
                        // Both banner action buttons use the tinted-on-
                        // gradient treatment so they read as siblings of
                        // the hero badges (white tint + thin white border)
                        // instead of competing with the gradient via the
                        // brand-gradient + neon glow. See #930.
                        if (onConsoleSettings != null) {
                            SpButton(
                                text = "Console settings",
                                onClick = onConsoleSettings,
                                style = SpButtonStyle.Tinted,
                                modifier = Modifier.autoFocus(),
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                        if (onBrowseGames != null) {
                            SpButton(
                                text = "Browse games",
                                onClick = onBrowseGames,
                                style = SpButtonStyle.Tinted,
                                modifier = Modifier.testTag(
                                    com.spela.player.presentation.ui.TestTags
                                        .consoleBrowseGames(console.id),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureInfoRow(label: String) {
    Row(verticalAlignment = Alignment.Top) {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "Yes",
                style = SpTypography.BodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.90f),
            )
            Text(
                text = label,
                style = SpTypography.LabelSmall,
                color = Color.White.copy(alpha = 0.40f),
            )
        }
        Spacer(Modifier.width(SpSpacing.XSmall))
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(SpSpacing.IconSmall),
        )
    }
}

@Composable
internal fun MetadataBadge(
    icon: @Composable () -> Unit,
    label: String,
    backgroundColor: Color = HeroBadgeBackground,
    textColor: Color = HeroTextPrimary,
) {
    Row(
        modifier = Modifier
            .background(
                backgroundColor,
                RoundedCornerShape(SpSpacing.RadiusPill),
            )
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

/**
 * An expandable "About" card shown below the hero banner when [Console.summary]
 * is available. Collapsed by default to keep the screen tidy.
 */
@Composable
internal fun ConsoleAboutSection(
    console: Console,
    modifier: Modifier = Modifier,
) {
    val summary = console.summary
    if (summary.isNullOrBlank()) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = summary,
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            modifier = Modifier.fillMaxWidth(),
        )
        SpButton(
            text = if (expanded) "Less" else "More",
            onClick = { expanded = !expanded },
            style = SpButtonStyle.Ghost,
            onGradient = true,
            modifier = Modifier.align(Alignment.End),
            leadingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }
}

