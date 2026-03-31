# Neon UI Redesign — Design Spec

**Date:** 2026-03-31
**Status:** Approved

## Goal

Align the app's UI components with the vibrant, neon-glowing Spela logo. The current form elements (inputs, buttons) are flat and utilitarian. The redesign brings the logo's retro-neon gaming energy into the core UI components app-wide.

## Design Direction

**Retro-neon gaming aesthetic.** The logo's color palette (blue `#6B8DD6`, purple `#a855f7`, pink `#E056A0`, orange `#F7A84B`) is used as accent colors throughout — gradient borders on inputs, colorful focus states, gradient primary buttons with glow.

## Gradient Palette

The "Spela gradient" used across components:

```
Linear gradient 135deg: #6B8DD6 → #a855f7 → #E056A0
```

This is used for: focused input borders, primary button fills, secondary button borders.

## Component Changes

All changes are **app-wide defaults**, not screen-specific variants.

### SpTextField

| State | Border | Background | Label color | Glow |
|-------|--------|------------|-------------|------|
| Unfocused | 1px `rgba(255,255,255,0.15)` | Transparent | `OnBackgroundSecondary` | None |
| Focused | 1.5px Spela gradient (border-box trick) | Transparent | Accent purple `#a78bfa` | `box-shadow: 0 0 20px rgba(139,92,246,0.15), 0 0 40px rgba(224,86,160,0.08)` |
| Error | 1.5px `Error` color | Transparent | `Error` | Subtle red glow |
| Disabled | 1px `rgba(255,255,255,0.06)` | Transparent | `OnBackgroundTertiary` | None |

Key changes from current:
- **Remove `SurfaceVariant` container fill** — inputs are now transparent, letting the background show through
- **Gradient border on focus** instead of single-color `Primary` border
- **Neon glow shadow on focus** — subtle purple/pink outer glow
- **Unfocused border bumped to 15% white** (from current ~10%) so inputs don't look disabled

Implementation note: Compose `OutlinedTextField` doesn't natively support gradient borders. Use `Modifier.drawBehind` or a `Box` wrapper with `Modifier.border(brush = ...)` to achieve the gradient border effect. The glow is a custom shadow drawn via `drawBehind` with blur.

### SpButton

#### Primary (`SpButtonStyle.Primary`)

- **Container:** Spela gradient fill (`linear-gradient(135deg, #6B8DD6, #a855f7, #E056A0)`)
- **Glow:** `box-shadow: 0 0 25px rgba(139,92,246,0.3), 0 0 50px rgba(224,86,160,0.12)`
- **Text:** White, bold
- **Disabled:** `SurfaceBright` fill, no glow (unchanged)

Implementation note: Use `Brush.linearGradient` for the background. The glow is drawn via `Modifier.drawBehind` with `drawRoundRect` using a blurred paint.

#### Secondary / Outlined (`SpButtonStyle.Secondary`, `SpButtonStyle.Outlined`)

- **Container:** Transparent
- **Border:** 1.5px Spela gradient (same border-box technique as inputs)
- **Glow:** Same purple/pink glow as inputs but slightly stronger
- **Text:** White
- **Disabled:** Subtle border, no glow

#### Ghost (`SpButtonStyle.Ghost`)

- **No changes** — accent color text, no background (already works well)

### Server Selector (new component: `SpServerPill`)

A new composable for the login screen's server indicator. Replaces the current plain tappable box.

- **Shape:** Full-radius pill (stadium)
- **Border:** Spela gradient at 40% opacity
- **Background:** Transparent
- **Content (left to right):**
  1. Green status dot (8px) with green glow shadow
  2. Server URL text in accent purple (`#c4b5fd`)
  3. Vertical divider line (`rgba(255,255,255,0.12)`, 1px wide, 16px tall)
  4. "Switch" label in accent purple (`#a78bfa`)
- **On tap:** Calls `onChangeServer()`

## Screen Changes

### LoginScreen

- Replace `SpBrandedBackgroundColor` flat fill with `SpGradientBackground` (adds ambient glow blobs)
- Replace "S" box icon → `SpLogo` (already done)
- Replace server URL box with `SpServerPill`
- Form fields and buttons automatically get the new styling

### ServerConnectionScreen

- Already uses `SpGradientBackground` (no change needed)
- Form fields and buttons automatically get the new styling

## Scope

These are the only files that need changes:

1. **`SpTextField.kt`** — Gradient border + glow on focus, transparent background
2. **`SpButton.kt`** — Gradient fill + glow for Primary, gradient border + glow for Secondary/Outlined
3. **`LoginScreen.kt`** — Use `SpGradientBackground`, add `SpServerPill`
4. **New: `SpServerPill.kt`** — Server indicator pill component

## Out of Scope

- No changes to `SpCard`, `SpSnackbar`, `SpLoadingIndicator`, or other components
- No layout changes to `ServerConnectionScreen` (already redesigned)
- No changes to web UI components (web has its own design system)
