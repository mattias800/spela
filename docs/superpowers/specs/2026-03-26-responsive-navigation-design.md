# Responsive Navigation — Bottom Bar to Side Rail

**Date:** 2026-03-26
**Status:** Approved

## Overview

Make the app's tab navigation responsive: phones keep the bottom bar, larger screens get a side rail. Three tiers based on available width. Gamepad mode (L1/R1 section indicator) is completely unaffected.

## Responsive Breakpoints

| Width | Layout | Nav Style |
|-------|--------|-----------|
| < 600dp | Bottom bar | Current `SpBottomNavBar` (unchanged) |
| 600–840dp | Icon-only side rail | ~72dp wide, icons vertically listed |
| > 840dp | Labeled side rail | ~200dp wide, icon + label |

Detection uses `BoxWithConstraints` checking `maxWidth`, following the existing pattern in `GameDetailLayout`.

```kotlin
enum class NavigationLayoutMode {
    BOTTOM_BAR,      // < 600dp
    ICON_RAIL,       // 600-840dp
    LABELED_RAIL,    // > 840dp
}
```

## Side Rail Layout

- **Top-aligned:** First 5 items (Home, Explore, Consoles, Collections, Activity) at the top
- **Settings at bottom:** Separated by a weighted spacer, anchored to the bottom of the rail
- **Background:** `SurfaceVariant`, no border or shadow
- **Selected item:** `Primary` color icon (+ text in labeled mode)
- **Unselected item:** `OnBackgroundTertiary` dimmed
- **Item height:** ~56dp (comfortable touch/click target)
- **Icon-only width:** ~72dp
- **Labeled width:** ~200dp

## Integration into SpelaApp

The layout in `SpelaApp.kt` changes from a vertical `Column` (content + bottom bar) to a conditional layout:

```
BoxWithConstraints {
    Row {
        // Side rail (if >= 600dp, touch mode, nav visible)
        SpNavigationRail(...)

        Column(weight 1f) {
            content (weight 1f)
            // Bottom bar (if < 600dp, touch mode, nav visible)
            SpBottomNavBar(...)
        }
    }

    // Gamepad section indicator overlay (unchanged)
    SpSectionIndicator(...)
}
```

## What Changes

**New:**
- `SpNavigationRail` composable — side rail with `showLabels: Boolean` parameter
- `NavigationLayoutMode` enum — three layout tiers
- Layout switch logic in `SpelaApp.kt` — `BoxWithConstraints` + `Row` wrapping

**Unchanged:**
- `SpBottomNavBar` — still used for phones, no modifications
- `SpSectionIndicator` — still used for gamepad mode at all sizes, no modifications
- `NavigationViewModel` — no changes to navigation state or intents
- L1/R1 tab switching — completely unaffected
- All screen composables — no changes needed
- Tab visibility rules — same conditions (hidden on login/connection screens, hidden in gamepad mode)

## New Composable: SpNavigationRail

Takes the same core parameters as `SpBottomNavBar`:
- `selectedTab: BottomNavTab`
- `onTabClick: (BottomNavTab) -> Unit`

Plus:
- `showLabels: Boolean` — false for icon-only (600-840dp), true for labeled (>840dp)

Renders a `Column` with the 6 `BottomNavTab` entries. Settings is separated from the first 5 by `Spacer(Modifier.weight(1f))`.

## Testing

Desktop E2E tests:
- Rail renders with icons when container width > 600dp
- Rail renders with labels when container width > 840dp
- Bottom bar renders when container width < 600dp
- Tab click on rail triggers navigation callback
- Settings item is bottom-aligned (separated by spacer)
- Rail hidden in gamepad mode (section indicator shown instead)
- Rail hidden on login/connection screens

No Android smoke tests needed — purely shared UI code.

## Out of Scope

- Collapsible/expandable rail (hover to expand)
- Rail width configuration or user preference
- Changes to gamepad mode or section indicator
- Changes to screen composables or navigation logic
