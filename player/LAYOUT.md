# Player App Layout System

The player app uses shared layout composables to enforce consistent screen structure.
**No screen should use custom padding, scroll, or background code.** All layout is
composed from the components below.

## Layout Composables

### `SpScreen` — Root container
Every screen's outermost composable. Provides background (solid or gradient),
fills available space. Uses `Box` internally so overlays (snackbars, FABs) work.

**Does NOT scroll. Does NOT add padding.**

```kotlin
SpScreen {
    // content (BoxScope)
}

// With gradient background:
SpScreen(gradientColors = listOf(color1, color2)) {
    // content
}
```

### `SpScrollableContent` — Scrollable column
Vertical scroll container. Place as the first child of `SpScreen`.
Overlays (snackbars) go as siblings after it.

```kotlin
SpScreen {
    SpScrollableContent {
        // scrollable content (ColumnScope)
    }
    SpSnackbar(modifier = Modifier.align(Alignment.BottomCenter))
}
```

### `SpScreenTopSpacer` — Pill clearance
Adds space for the section indicator pill in gamepad mode.
Renders nothing in touch mode. Use on screens that don't have
edge-to-edge content (hero banners) at the top.

### `SpMainContentPadding` — Content insets
Adds horizontal padding (`SpSpacing.ScreenHorizontal` = 20dp on each side)
and a small top gap (`SpSpacing.Large` = 20dp). Use to wrap the main
content area below any edge-to-edge elements.

### `SpSectionList` — Vertical section gaps
Simple `Column` with `SpSpacing.Large` (20dp) vertical spacing between
children. Typically contains `SpTitledSection` items. Does NOT scroll,
does NOT add padding.

### `SpCarousel` — Horizontal item list
`LazyRow` with `focusGroup` + `focusRestorer` + d-pad wrap-around.
Remembers which item had focus when you leave and return.

## Screen Patterns

### Screen with hero banner (Explore)
Banner goes edge-to-edge. Sections below get padding.

```kotlin
SpScreen {
    SpScrollableContent {
        HeroBanner()                    // edge-to-edge, no padding
        SpMainContentPadding {          // horizontal padding + top gap
            SpSectionList {             // vertical gaps between sections
                SpTitledSection { ... }
                SpTitledSection { ... }
            }
        }
    }
    SpSnackbar(modifier = Modifier.align(Alignment.BottomCenter))
}
```

### Screen with pill clearance (Console List)
No hero banner — needs `SpScreenTopSpacer` for the gamepad pill.

```kotlin
SpScreen(gradientColors = gradientColors) {
    SpScrollableContent {
        SpScreenTopSpacer()             // clears pill in gamepad mode
        SpMainContentPadding {
            ConsolesGrid(...)
        }
    }
}
```

### Screen with top bar (Search, Settings)
Touch mode shows `SpTopBar`, gamepad mode shows pill clearance.

```kotlin
SpScreen {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!isGamepad) {
            SpTopBar(title = "Search", showBack = true, onBack = onBack)
        }
        SpMainContentPadding {
            SearchField(...)
            Results(...)
        }
    }
    SpSnackbar(modifier = Modifier.align(Alignment.BottomCenter))
}
```

### Screen with custom top content (Home)
Custom heading row inside `SpMainContentPadding` — no spacer needed
because the heading IS the top content.

```kotlin
SpScreen(gradientColors = gradientColors) {
    PullToRefreshBox(...) {
        SpScrollableContent {
            SpMainContentPadding {
                SpSectionList {
                    HeadingRow(...)
                    SpTitledSection { ... }
                }
            }
        }
    }
    SpSnackbar(modifier = Modifier.align(Alignment.BottomCenter))
}
```

## Rules

1. **No custom `Box(Modifier.fillMaxSize().spScreenBackground())`** — use `SpScreen`.
2. **No custom `Modifier.padding(horizontal = SpSpacing.ScreenHorizontal)`** on screens — use `SpMainContentPadding`.
3. **No `LazyColumn` for screen-level scroll** — use `SpScrollableContent`.
4. **No custom vertical spacing between sections** — use `SpSectionList`.
5. **Edge-to-edge content** (banners, hero images) goes as a direct child of `SpScrollableContent`, before `SpMainContentPadding`.
6. **Overlays** (snackbars, FABs) go as siblings of `SpScrollableContent` inside `SpScreen` (BoxScope).

## File locations

| Component | File |
|-----------|------|
| `SpScreen` | `components/SpScreen.kt` |
| `SpScrollableContent` | `components/SpScreen.kt` |
| `SpMainContentPadding` | `components/SpScreen.kt` |
| `SpScreenTopSpacer` | `components/SpScreenTopSpacer.kt` |
| `SpSectionList` | `components/SpSectionList.kt` |
| `SpCarousel` | `components/SpCarousel.kt` |
| `SpTitledSection` | `components/SpTitledSection.kt` |
