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

---

# Focus Memory and Restoration

This section documents the focus restoration system — the rules that
keep focus continuous as the user navigates forward and back between
screens, and what to do when adding a new screen or focusable widget.

## The Problem

Compose's focus model is per-composition. Every time a screen is
disposed and re-mounted (forward/back navigation, tab switch), focus
state is lost: nothing is focused, and the user has no way to navigate
with keyboard or d-pad until they Tab/click somewhere.

Worse, `Modifier.focusRequester(...)` only accepts requests for layout
nodes that are themselves focusable. Calling `requestFocus()` on a
non-focusable container often throws or silently no-ops, and the
behavior has changed across Compose versions.

A naïve "request focus on the first card after a delay" approach also
breaks because:

- Multiple containers race when they each schedule their own
  `LaunchedEffect(delay) { requestFocus() }` — the last to fire wins,
  not the most-recently-focused one.
- During an `AnimatedContent` back transition, the **outgoing** screen
  is still composed for the duration of the slide. Its focus restorer
  re-evaluates as `LocalIsForwardNavigation` flips to `false`, and a
  newly-true `shouldRestore` re-fires `requestFocus()`, stealing focus
  back from the destination screen.

## The Solution: One Primitive

Everything funnels through one modifier:

```kotlin
fun Modifier.focusRestoreItem(
    key: String,
    isDefault: Boolean = false,
    requester: FocusRequester? = null,
): Modifier
```

It does three things:

1. **Save**: when this element (or any descendant) gains focus, writes
   `key` into the enclosing `LocalFocusMemory` scope.
2. **Restore**: on screen entry (back-nav or tab-switch), if the
   scope's saved value matches `key`, requests focus on this element
   after a brief layout-settle delay (~120 ms).
3. **Default focus**: when `isDefault = true` and the scope is empty
   (first-ever entry to the screen), claims focus on this element
   instead — the "sensible default focus" behavior the user expects.

There's exactly one saved key per scope, so by construction only the
most-recently-focused element restores. Sibling elements keep no state
to race over.

### Critical: capture-once-on-mount

The firing decision is captured exactly once at first composition, via
`remember { ... }`:

```kotlin
val initialAction = remember {
    when {
        scope.value == key && !isForward -> Action.Restore
        scope.value.isEmpty() && isDefault -> Action.Default
        else -> Action.None
    }
}
```

If we re-evaluated `shouldRestore` on every recomposition, an
**outgoing** screen during an AnimatedContent transition would see
`isForward` flip from `true` to `false`, satisfy `shouldRestore`,
re-enter the `LaunchedEffect`, and fire `requestFocus()` a second time
— stealing focus from the destination screen. This bug is invisible in
test mode (animations disabled) and was the root cause of the "focus
restored, then immediately lost" symptom; do not "simplify" the
`remember { }` away.

## Per-Screen Scope: `LocalFocusMemory`

Each screen that wants focus restoration provides its own scope at the
root:

```kotlin
@Composable
fun MyScreen(...) {
    val focusMemory = rememberFocusMemoryState()
    CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        // screen content
    }
}
```

`rememberFocusMemoryState()` returns a `MutableState<String>` backed
by `rememberSaveable`, so the saved key survives screen disposal
across forward+back nav (via the per-route `SaveableStateHolder` in
`SpelaApp.kt`).

One scope per screen is the intended granularity. Multiple carousels,
grids, or buttons within the same screen share the same scope — that's
why "active carousel last focused" naturally wins over siblings on
back-nav, with no separate gating CompositionLocal.

## Carousels: SpCarousel

`SpCarousel` opts into the system via two parameters:

```kotlin
SpCarousel(
    itemCount = games.size,
    memoryKey = "home_continue_playing",
    itemKey = { games[it].id },
    isDefaultFocusGroup = true, // optional, only one per screen
) { index, focusRequester ->
    // content
}
```

Internally, the carousel applies `focusRestoreItem("$memoryKey/${itemKey(i)}")`
to each item's outer Box, with `isDefault = isDefaultFocusGroup && i == 0`.
Item-level focus is restored to the same game on back-nav, even if the
underlying list reordered.

`isDefaultFocusGroup` should be set on **at most one** SpCarousel per
screen — this is the carousel that "owns" first-entry focus. On the
Home screen this is Continue Playing.

## Section-Level Fallback: `Modifier.rememberFocus`

```kotlin
SpTitledSection(
    title = "Continue Playing",
    modifier = Modifier.rememberFocus("section_continue_playing"),
) { ... }
```

A restoration-only fallback for section containers. It does NOT save
its key when descendants gain focus — that role belongs to the
leaf-level `focusRestoreItem` calls (or `SpCarousel`'s internal use of
it). Without this asymmetry, the section's `onFocusChanged` would fire
last in the propagation order and clobber the more-specific item key
that an item just wrote.

In practice, `rememberFocus` only matters when a section contains
focusable elements that don't themselves use `focusRestoreItem` — it
gives back-nav something to land on (the section's first focusable
descendant) when no item key matches.

## Default Focus on Forward Entry

`Modifier.focusRestoreItem(key, isDefault = true)` is the single
primitive for default-on-entry focus. It saves the focused element's
key to the enclosing `LocalFocusMemory` scope, restores focus on
back-nav when the saved key matches, and acts as the screen's default
on first entry when the saved key is empty.

The legacy `Modifier.autoFocus()` was retired in #1138. New code must
use `focusRestoreItem` — `autoFocus` fired on every forward navigation
but never saved focus, so it didn't compose with the rest of the
system.

## What to Do for a New Screen

For most screens, follow the recipe:

1. **Provide `LocalFocusMemory`** at the screen root.
2. **For each list/grid item**, apply
   `Modifier.focusRestoreItem(key = "<screen>_<itemId>", isDefault = (item == list.firstOrNull()))`.
3. **For SpCarousels**, pass `memoryKey` + `itemKey` (and
   `isDefaultFocusGroup = true` on at most one carousel per screen).
4. **For static-button screens** (no list), use
   `Modifier.focusRestoreItem(key = "<screen>_<button>", isDefault = true)`
   on the primary action.
5. Section containers that sit above SpCarousels can keep
   `Modifier.rememberFocus("section_xyz")` as a fallback; new screens
   typically don't need it.

Only **one** element per `LocalFocusMemory` scope should set
`isDefault = true`. If multiple do, the first to fire claims focus and
the rest skip — so this is safe but you'll typically want a deliberate
choice.

## Testing

The Compose-multiplatform `runComposeUiTest` harness defaults to
`LocalAnimationsEnabled = false` — `AnimatedContent` is bypassed and
screen swaps are instantaneous. Many focus-timing bugs (most notably
the LaunchedEffect re-fire described above) are silently masked in
this mode.

For tests that need to catch timing-sensitive focus bugs:

```kotlin
setContent { harness.App(animationsEnabled = true) }
```

The reference test is `HomeContinuePlayingFocusRestoreTest
.continuePlaying_focusRestoredAfterKeyboardEnterEscape` — it walks the
exact user path (Right keys to a non-first item, Enter to forward,
Escape to back) with animations enabled, and asserts the saved item
regains focus.

Two coverage gaps to know about:

- Some screens render no focusable items in the harness because they
  depend on data that the fake repos don't seed (e.g. GameDetail's
  Play button only appears when the game is cached or instant-download
  eligible). Tests that need that behavior must seed `gameRepo.games`
  with a non-zero `fileSize`.
- Lazy lists (LazyVerticalGrid, LazyColumn) dispose off-screen items.
  `performClick` on a card scrolled past the viewport edge will
  silently no-op because the card isn't in the semantics tree at that
  moment.

## Pitfalls

- **Don't share a `FocusRequester` between two layout nodes.** If both
  the carousel content and its outer Box attach the same requester,
  `requestFocus()` becomes ambiguous and silently no-ops in some cases
  (notably during AnimatedContent transitions). `focusRestoreItem`
  always uses its own internal requester unless you explicitly pass
  one (and you almost never should).
- **Don't put `Modifier.rememberFocus` on a section AND
  `Modifier.focusRestoreItem(isDefault = true)` on a child** — both
  will fire and race. The carousel's own `isDefaultFocusGroup = true`
  handles this when the section content is a carousel.
- **Don't add `removeState(...)` calls anywhere** — `SpelaApp` already
  manages saveable-state cleanup at the right moments (forward push
  removes the destination's preserved state; back-nav preserves both
  source and destination).
- **Don't catch-and-ignore exceptions from `requestFocus()` without a
  re-validation step.** The current implementation re-reads
  `scope.value` after the delay so a faster sibling or a user action
  during the 120 ms window wins over us; preserve that pattern when
  modifying the primitive.
- **Don't gate `focusRestoreItem` on `LocalInputMode`.** The whole
  system was already gamepad-gated before this rewrite and broke for
  keyboard users on desktop. Behavior is input-mode-agnostic by
  design.

## Cold-Load Skeleton vs. Pull-to-Refresh

Tangentially related (#1135): screens that render a `PullToRefreshBox`
should NOT use it for the cold-load state. The PullToRefreshBox
spinner only makes sense for refreshing already-loaded data; a fresh
screen should show a skeleton or centered loader.

Pattern (already used by `HomeScreen`, `ConsoleScreen`,
`ConsoleGamesScreen`):

```kotlin
val hasDataOnMount = state.list.isNotEmpty()
var sawLoading by remember(routeKey) { mutableStateOf(false) }
var hasInitiallyLoaded by remember(routeKey) { mutableStateOf(hasDataOnMount) }
if (state.isLoading) sawLoading = true
if (sawLoading && !state.isLoading) hasInitiallyLoaded = true

if (!hasInitiallyLoaded) {
    // Skeleton or centered loader, NO PullToRefreshBox
    return@SpScreen
}
PullToRefreshBox(isRefreshing = state.isLoading, ...) { ... }
```
