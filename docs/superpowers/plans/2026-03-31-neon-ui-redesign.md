# Neon UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle SpButton and SpTextField with neon gradient borders/fills and glow effects, add SpServerPill component, update LoginScreen to use gradient background.

**Architecture:** Define the Spela gradient palette in SpColor (single source of truth). SpTextField wraps OutlinedTextField in a Box that draws gradient border + glow via `Modifier.border(brush)` and `Modifier.drawBehind`. SpButton uses `Brush.linearGradient` background for Primary, gradient border for Secondary/Outlined. SpServerPill is a new composable used only by LoginScreen.

**Tech Stack:** Kotlin, Compose Multiplatform, `Brush.linearGradient`, `Modifier.border(width, brush, shape)`, `Modifier.drawBehind`

**Spec:** `docs/superpowers/specs/2026-03-31-neon-ui-redesign-design.md`

---

### Task 1: Add Spela gradient palette to SpColor

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/theme/SpColor.kt`

- [ ] **Step 1: Add gradient colors and brush builder**

Add these to the `SpColor` object, after the `Divider` section:

```kotlin
// Spela brand gradient — used for focused input borders, primary buttons, secondary button borders
val GradientStart = Color(0xFF6B8DD6)   // blue
val GradientMid = Color(0xFFa855f7)     // purple
val GradientEnd = Color(0xFFE056A0)     // pink
val AccentPurple = Color(0xFFA78BFA)    // label accent
val AccentPurpleLight = Color(0xFFC4B5FD) // lighter accent for text
```

Add this function after the `SpColor` object:

```kotlin
/**
 * Returns the Spela brand gradient brush (blue → purple → pink at 135°).
 * Used for focused input borders, primary button fills, and secondary button borders.
 */
fun spelaBrandGradient(
    startOffset: Offset = Offset.Zero,
    endOffset: Offset = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
): Brush = Brush.linearGradient(
    colors = listOf(SpColor.GradientStart, SpColor.GradientMid, SpColor.GradientEnd),
    start = startOffset,
    end = endOffset,
)
```

- [ ] **Step 2: Build and verify no compilation errors**

Run: `cd player && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/theme/SpColor.kt
git commit -m "feat: add Spela brand gradient palette to SpColor"
```

---

### Task 2: Restyle SpButton with gradient fill and glow

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpButton.kt`

- [ ] **Step 1: Add neon glow modifier**

Add a private helper function at the bottom of the file (before the closing brace or after `SpSecondaryButton`):

```kotlin
/**
 * Draws a neon glow behind the composable — two layered blurred rounded rects.
 */
private fun Modifier.neonGlow(
    shape: Shape,
    purple: Color = Color(0xFF8B5CF6),
    pink: Color = Color(0xFFE056A0),
    purpleAlpha: Float = 0.3f,
    pinkAlpha: Float = 0.12f,
): Modifier = this.drawBehind {
    val cornerRadius = when (shape) {
        is RoundedCornerShape -> shape.topStart.toPx(size, this)
        else -> SpSpacing.RadiusLarge.toPx()
    }
    // Purple glow — wider
    drawRoundRect(
        color = purple.copy(alpha = purpleAlpha),
        cornerRadius = CornerRadius(cornerRadius),
        size = size,
        style = Stroke(width = 25.dp.toPx()),
        blendMode = BlendMode.Screen,
    )
    // Pink glow — even wider, fainter
    drawRoundRect(
        color = pink.copy(alpha = pinkAlpha),
        cornerRadius = CornerRadius(cornerRadius),
        size = size,
        style = Stroke(width = 50.dp.toPx()),
        blendMode = BlendMode.Screen,
    )
}
```

Add necessary imports at the top:

```kotlin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.spela.player.presentation.ui.theme.spelaBrandGradient
```

- [ ] **Step 2: Update Primary style to gradient fill + glow**

Replace the entire `SpButtonStyle.Primary` branch with:

```kotlin
SpButtonStyle.Primary -> {
    val gradientBrush = spelaBrandGradient()
    Button(
        onClick = { if (!isLoading) onClick() },
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (enabled) Modifier.neonGlow(shape) else Modifier)
            .background(
                if (enabled) gradientBrush
                else Brush.linearGradient(listOf(SpColor.SurfaceBright, SpColor.SurfaceBright)),
                shape,
            )
            .then(focusMods),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = SpColor.OnBackgroundTertiary,
        ),
        contentPadding = if (isIconOnly) iconOnlyPadding else defaultPadding,
    ) {
        ButtonContent(text, isLoading, leadingIcon, Color.White)
    }
}
```

The gradient is drawn via `Modifier.background(brush, shape)` on the outer modifier, while `containerColor = Color.Transparent` makes the Button's own container invisible so the gradient shows through. The `neonGlow` modifier is applied before the background so the glow renders behind the gradient fill.

Add import: `import androidx.compose.ui.geometry.Offset`

- [ ] **Step 3: Update Secondary/Outlined style to gradient border + glow**

Replace the `SpButtonStyle.Outlined` branch:

```kotlin
SpButtonStyle.Outlined -> {
    val gradientBrush = spelaBrandGradient()
    OutlinedButton(
        onClick = { if (!isLoading) onClick() },
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (enabled) Modifier.neonGlow(shape, purpleAlpha = 0.2f, pinkAlpha = 0.08f) else Modifier)
            .then(focusMods),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        border = BorderStroke(
            1.5.dp,
            if (enabled) gradientBrush else Brush.linearGradient(listOf(SpColor.Divider, SpColor.Divider)),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            disabledContentColor = SpColor.OnBackgroundTertiary,
        ),
        contentPadding = if (isIconOnly) iconOnlyPadding else defaultPadding,
    ) {
        ButtonContent(text, isLoading, leadingIcon, Color.White)
    }
}
```

Update the `SpButtonStyle.Secondary` branch similarly — since `SpSecondaryButton` delegates to `SpButton` with `SpButtonStyle.Outlined`, just make the Secondary branch match:

```kotlin
SpButtonStyle.Secondary -> {
    val gradientBrush = spelaBrandGradient()
    Button(
        onClick = { if (!isLoading) onClick() },
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (enabled) Modifier.neonGlow(shape, purpleAlpha = 0.2f, pinkAlpha = 0.08f) else Modifier)
            .border(1.5.dp, if (enabled) gradientBrush else Brush.linearGradient(listOf(SpColor.Divider, SpColor.Divider)), shape)
            .then(focusMods),
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = SpColor.OnBackgroundTertiary,
        ),
        contentPadding = if (isIconOnly) iconOnlyPadding else defaultPadding,
    ) {
        ButtonContent(text, isLoading, leadingIcon, Color.White)
    }
}
```

- [ ] **Step 4: Build and verify**

Run: `cd player && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Deploy to device and visually verify buttons**

Run: `ANDROID_SERIAL=54071896 ./gradlew :android:installDebug`
Then: `adb -s 54071896 shell am force-stop com.spela.player && adb -s 54071896 shell am start -n com.spela.player/.android.MainActivity`

Verify: Primary button on server connection screen has gradient fill + glow. Secondary "Add Server" button has gradient border + glow.

- [ ] **Step 6: Commit**

```
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpButton.kt
git commit -m "feat: restyle SpButton with gradient fill and neon glow"
```

---

### Task 3: Restyle SpTextField with gradient border and glow on focus

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpTextField.kt`

- [ ] **Step 1: Replace OutlinedTextField colors for transparent background and brighter unfocused border**

Update the `OutlinedTextFieldDefaults.colors()` call:

```kotlin
colors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SpColor.OnBackground,
    unfocusedTextColor = SpColor.OnBackground,
    focusedBorderColor = SpColor.GradientMid,       // will be overridden by gradient border
    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
    errorBorderColor = SpColor.Error,
    focusedLabelColor = SpColor.AccentPurple,
    unfocusedLabelColor = SpColor.OnBackgroundSecondary,
    cursorColor = SpColor.AccentPurple,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    disabledBorderColor = Color.White.copy(alpha = 0.06f),
    disabledTextColor = SpColor.OnBackgroundTertiary,
    disabledLabelColor = SpColor.OnBackgroundTertiary,
),
```

- [ ] **Step 2: Wrap OutlinedTextField in Box with gradient border + glow on focus**

The approach: wrap the `OutlinedTextField` in a `Box`, and use `Modifier.border(brush, shape)` on the Box when focused. Also add a `drawBehind` glow modifier when focused.

This requires tracking focus state. Add a `FocusState` listener:

Add imports:

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import com.spela.player.presentation.ui.theme.spelaBrandGradient
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
```

Wrap the `OutlinedTextField` call. Replace:

```kotlin
Column(modifier = modifier) {
    OutlinedTextField(
        ...
    )
```

With:

```kotlin
Column(modifier = modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val glowModifier = if (isFocused && enabled && !isError && errorMessage == null) {
        Modifier.drawBehind {
            val cornerRadius = CornerRadius(SpSpacing.RadiusLarge.toPx())
            drawRoundRect(
                color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                cornerRadius = cornerRadius,
                size = size,
                style = Stroke(width = 20.dp.toPx()),
                blendMode = BlendMode.Screen,
            )
            drawRoundRect(
                color = Color(0xFFE056A0).copy(alpha = 0.08f),
                cornerRadius = cornerRadius,
                size = size,
                style = Stroke(width = 40.dp.toPx()),
                blendMode = BlendMode.Screen,
            )
        }
    } else Modifier

    OutlinedTextField(
        ...
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .then(glowModifier)
            .then(
                if (isFocused && enabled && !isError && errorMessage == null) {
                    Modifier.border(1.5.dp, spelaBrandGradient(), RoundedCornerShape(SpSpacing.RadiusLarge))
                } else Modifier
            ),
        ...
        // Set focusedBorderColor to Transparent when we draw our own gradient border
        colors = OutlinedTextFieldDefaults.colors(
            ...
            focusedBorderColor = Color.Transparent,
            ...
        ),
```

- [ ] **Step 3: Build and verify**

Run: `cd player && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Deploy to device and visually verify inputs**

Run: `ANDROID_SERIAL=54071896 ./gradlew :android:installDebug`
Then restart the app.

Verify: Text fields on server connection screen have transparent backgrounds. When tapped/focused, they show gradient border + neon glow. Unfocused fields have a visible but subtle white border.

- [ ] **Step 5: Commit**

```
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpTextField.kt
git commit -m "feat: restyle SpTextField with gradient border and neon glow on focus"
```

---

### Task 4: Create SpServerPill component

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpServerPill.kt`

- [ ] **Step 1: Create the SpServerPill composable**

```kotlin
package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.ui.theme.spelaBrandGradient

/**
 * Neon pill showing the connected server URL with a status dot and "Switch" action.
 * Used on the login screen as a compact server indicator.
 */
@Composable
fun SpServerPill(
    serverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            SpColor.GradientStart.copy(alpha = 0.4f),
            SpColor.GradientMid.copy(alpha = 0.4f),
            SpColor.GradientEnd.copy(alpha = 0.4f),
        ),
    )

    Row(
        modifier = modifier
            .clip(pillShape)
            .border(1.dp, gradientBrush, pillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Green status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(SpColor.Success),
        )

        // Server URL
        Text(
            text = serverUrl.ifEmpty { "No server" },
            style = SpTypography.BodySmall,
            color = SpColor.AccentPurpleLight,
            modifier = Modifier.padding(start = 8.dp),
        )

        // Vertical divider
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .width(1.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.12f)),
        )

        // Switch label
        Text(
            text = "Switch",
            style = SpTypography.LabelSmall,
            color = SpColor.AccentPurple,
        )
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `cd player && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpServerPill.kt
git commit -m "feat: add SpServerPill component with neon gradient border"
```

---

### Task 5: Update LoginScreen to use gradient background and SpServerPill

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/screen/LoginScreen.kt`

- [ ] **Step 1: Replace background with SpGradientBackground and swap server indicator**

Replace the import of `SpBrandedBackgroundColor`:

```kotlin
import com.spela.player.presentation.ui.components.SpGradientBackground
import com.spela.player.presentation.ui.components.SpServerPill
```

Remove the import of `SpBrandedBackgroundColor` (no longer needed directly).

Replace:

```kotlin
BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .background(SpBrandedBackgroundColor),
) {
    val isLandscape = maxWidth > maxHeight

    Column(
```

With:

```kotlin
BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isLandscape = maxWidth > maxHeight

    SpGradientBackground {
    Column(
```

Add the matching closing brace for `SpGradientBackground` after the Column's closing brace.

Replace the server URL indicator box (the `Box` with `SpColor.SurfaceVariant` background, lines ~109-134):

```kotlin
// Server URL indicator (tappable to change server)
Box(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
        .background(SpColor.SurfaceVariant)
        .clickable { onChangeServer() }
        .padding(SpSpacing.Medium),
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = serverUrl.ifEmpty { "No server configured" },
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Tap to change server",
            style = SpTypography.LabelSmall,
            color = SpColor.Primary,
            textAlign = TextAlign.Center,
        )
    }
}
```

With:

```kotlin
SpServerPill(
    serverUrl = serverUrl,
    onClick = onChangeServer,
)
```

- [ ] **Step 2: Clean up unused imports**

Remove unused imports: `SpBrandedBackgroundColor`, `clickable` (if no longer used), `RoundedCornerShape` (if no longer used), `SpColor.SurfaceVariant` references. Keep imports that are still used by other parts of the screen.

- [ ] **Step 3: Build and verify**

Run: `cd player && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Deploy to device and verify the complete login screen**

Run: `ANDROID_SERIAL=54071896 ./gradlew :android:installDebug`
Then restart the app and navigate to the login screen.

Verify:
- Gradient background with SNES glow blobs
- Spela logo displayed prominently
- Neon pill server selector with green dot and "| Switch"
- Text fields: transparent background, gradient border + glow on focus
- Primary "Sign In" button: gradient fill + glow
- Ghost "Register" link: accent color text

- [ ] **Step 5: Commit**

```
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/screen/LoginScreen.kt
git commit -m "feat: update LoginScreen with gradient background and SpServerPill"
```

---

### Task 6: Run full test suite and final verification

- [ ] **Step 1: Run desktop tests**

Run: `cd player && ./run-desktop-tests.sh`
Expected: All tests pass. The component changes should not break any existing tests since they're visual-only (colors/borders).

- [ ] **Step 2: Visual check on device — server connection screen**

Navigate through: server connection screen (portrait + landscape), verify gradient buttons and text fields look correct.

- [ ] **Step 3: Visual check on device — login screen**

Navigate through: login screen (portrait + landscape), verify gradient background, server pill, inputs, buttons.

- [ ] **Step 4: Visual check on device — other screens**

Navigate to settings, game detail, and library screens. Verify that buttons and text fields throughout the app have the new neon styling and nothing looks broken.

- [ ] **Step 5: Commit any fixes if needed, then final commit**

If any visual tweaks were needed during verification, commit them.
