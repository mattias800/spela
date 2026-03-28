# Gamepad Navigation Architecture

This document explains how gamepad/d-pad navigation works in the Spela
player app. It covers the focus system, the patterns that make it work,
and the pitfalls to avoid.

## Overview

The app supports two input modes: **TOUCH** and **GAMEPAD**. When a
physical controller is detected, the app switches to gamepad mode which:

- Hides the bottom nav bar (replaced by L1/R1 section indicator pill)
- Hides the `SpTopBar` (back + action buttons) on all screens
- Enables d-pad focus navigation through all content
- Enables right stick scrolling with automatic focus management

The input mode is provided to all composables via `LocalInputMode`
(a `CompositionLocal` defined in `InputMode.kt`).

## Core Component: GamepadHandler

`GamepadHandler` in `GamepadNavigation.kt` wraps the entire app content.
It handles:

### Focus Acquisition on Screen Change

When the screen changes (tab switch, forward navigation), the handler
requests focus on its wrapper Box via a `FocusRequester`. This puts the
Box itself into a focused state. The first d-pad press then calls
`moveFocus(FocusDirection.Next)` to enter the content tree and land on
the first focusable element.

**Why not call `moveFocus(Next)` directly from the coroutine?**
Compose's `moveFocus` from a `LaunchedEffect` coroutine doesn't
reliably find focusable children inside `AnimatedContent` transitions.
But `moveFocus` from a key event handler (d-pad press) does. So we
split it: coroutine requests focus on the wrapper, key handler enters
the content.

### Self-Focus Detection

The handler tracks two states:
- `hasFocus` — true when anything in the tree has focus
- `isSelfFocused` — true when the wrapper Box itself has direct focus

When `isSelfFocused` is true (or `hasFocus` is false), any d-pad press
calls `moveFocus(Next)` instead of directional movement. This is because
directional moves (Up/Down/Left/Right) use **spatial reasoning** — they
look for elements geometrically in that direction. A full-screen Box has
nothing spatially above or below it, so directional moves always fail.
`moveFocus(Next)` uses **tree traversal** which finds the first
focusable descendant regardless of position.

Once a child element has focus, normal directional navigation takes over.

### Right Stick Scroll

When the right analog stick is used for scrolling (`MainActivity.kt`),
the activity calls `currentFocus?.clearFocus()` to break the link
between the focused element and the d-pad. Without this, the next d-pad
press would jump the scroll position back to the (now offscreen) focused
element. After clearing, the next d-pad press triggers the recovery path
and focuses the first visible element.

## Focus Ring: `spFocusRing` Modifier

Defined in `GamepadNavigation.kt`. Adds a white border (0.85 alpha) and
optional scale animation when the element (or any descendant) has focus.

```kotlin
Modifier.spFocusRing(shape = RoundedCornerShape(12.dp), scaleOnFocus = true)
```

**Key detail:** Uses `state.isFocused || state.hasFocus` in
`onFocusChanged`. This is necessary because `spFocusRing` is often
placed ABOVE the `focusable()` modifier in the chain. In that case,
the focusable child gets `isFocused = true` but `spFocusRing` only
sees `hasFocus = true`. Checking both ensures the ring lights up
regardless of where in the modifier chain `focusable()` appears.

## Making Components Focusable

### The Rule

Every interactive element must have explicit `Modifier.focusable()` to
participate in d-pad navigation. Do NOT rely on Material3 components
(Button, IconButton, etc.) being focusable — their internal focus
handling doesn't register in Compose's focus traversal tree.

### SpButton

`SpButton` adds `spFocusRing()` + `focusable()` to its outer modifier:

```kotlin
val focusMods = Modifier
    .spFocusRing(shape = shape)
    .focusable(interactionSource = interactionSource)
```

This is applied via `.then(focusMods)` on every button style variant.

### SpCard / SpInnerCard

These use `collectIsFocusedAsState()` from the interaction source and
draw their own focus border (white, 0.85 alpha, 2dp). They add
`.focusable(interactionSource)` when `onClick` is provided.

### ConsoleCard

Uses `spFocusRing(shape, scaleOnFocus = true)` placed BEFORE
`graphicsLayer`, `shadow`, and `clip` in the modifier chain. If placed
after `clip`, the focus ring border gets clipped and becomes invisible.

### SpFab

Shared floating action button with `spFocusRing(CircleShape)` +
`focusable()`. White semi-transparent background that blends with any
gradient.

## Layout Patterns for Focus Traversal

### `focusGroup()` on Containers

`SpSectionList` (the shared LazyColumn wrapper) has `focusGroup()` on
the LazyColumn. This enables focus to traverse from overlaid elements
(like SpTopBar) into the scroll content.

The `ConsoleHeroBanner` also has `focusGroup()` so focus can traverse
into the buttons inside the banner overlay.

### Spatial Focus and Overlays

Compose's directional focus (Up/Down/Left/Right) uses **spatial
reasoning** — it looks for focusable elements geometrically in the
pressed direction. This means:

- Buttons inside a `Box(Alignment.BottomEnd)` overlay CAN be reached
  by d-pad as long as they are `focusable()` (via SpButton's explicit
  focusable modifier)
- `moveFocus(Next)` finds elements in **composition order**, not spatial
  order. The first composed focusable element gets focus first.
- Items in a `LazyColumn` are only composed when visible. Buttons in an
  offscreen item won't be found by `moveFocus(Next)`.

### SpTopBar Auto-Hide

`SpTopBar` checks `LocalInputMode` and returns early in GAMEPAD mode.
This removes the back/settings buttons from the focus tree entirely,
preventing focus from getting stuck on overlay buttons.

`SpSectionList` automatically reduces top padding in gamepad mode since
the top bar space is no longer needed.

## Color System

All focus indicators use **white** (no indigo/primary color). This
ensures visibility against any console-branded gradient background.

- Focus borders: `Color.White.copy(alpha = 0.85f)`
- Focus scale: 1.04x (when `scaleOnFocus = true`)
- Tab/pill focus backgrounds: `Color.Black.copy(alpha = 0.3f)`
- Section indicator pill: `Color.Black.copy(alpha = 0.6f)` background

## Per-Tab Navigation Stacks

Each of the 6 tabs maintains its own `List<SpScreen>` back stack.
`activeTab` is tracked explicitly in `NavigationState` — there is no
`activeTabForScreen()` mapping. Screens don't "belong" to tabs.

- **NavigateTo**: pushes onto the active tab's stack
- **SwitchTab**: changes `activeTab`, preserves all stacks
- **GoBack**: pops from active tab; at root, switches to Home
- **L1/R1**: cycles `activeTab` with stacks preserved

## Checklist for New Screens

1. Use `SpSectionList` for scrollable content (has `focusGroup()`)
2. Use `SpButton` for buttons (has `focusable()` + `spFocusRing()`)
3. Use `SpCard`/`SpInnerCard` with `onClick` for focusable cards
4. Don't add `SpTopBar` visibility logic — it auto-hides in gamepad mode
5. If adding custom interactive elements, add `spFocusRing()` +
   `focusable()` + `clickable()` explicitly
6. Don't rely on Material3 components being focusable — always add
   explicit `focusable()`
7. Place `spFocusRing()` BEFORE `clip()` and `shadow()` in the modifier
   chain, or the border will be clipped

## Checklist for New Components

1. If clickable, add `Modifier.focusable()` explicitly
2. Add `spFocusRing(shape)` for visual focus indication
3. Use white-based colors for focus indicators (no theme colors)
4. Test with d-pad navigation, not just touch/mouse
