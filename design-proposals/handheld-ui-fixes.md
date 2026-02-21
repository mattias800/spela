# Handheld UI Fixes - Design Specifications

## Context
These specifications address 8 UI issues primarily affecting the Ayn Thor handheld (landscape, small screen ~6-7 inch, approximately 1280x720 or 1920x1080). The Ayn Thor is a landscape-first device similar to a Steam Deck or Nintendo Switch Lite. Vertical space is extremely limited in landscape orientation.

---

## Issue 1: Squashed Resume/New Game Buttons

### Current Problem
In `GameDetailScreen.kt` lines 374-417, the "Resume" and "New Game" buttons share a `Row` with `Modifier.weight(1f)`. On landscape handhelds, the right-side content panel in `GameDetailLayout` (60% of screen width) has limited horizontal space. With 3 icon-only buttons also in the row (Favorite, Play Later, Add to Collection - each ~48dp + spacing), the weighted text buttons get squeezed so narrow that text wraps vertically.

The button row layout is:
```
[Resume (weight 1f)] [New Game (weight 1f)] [Heart] [Clock] [Library+]
```

On a 1280px-wide landscape screen, the right panel is ~768px. With 20dp screen padding on each side and 3 icon buttons at ~48dp each plus 12dp spacing between 5 items = 48dp, usable width for the 2 text buttons is approximately: 768 - 40 - 144 - 48 = 536px shared between 2 buttons = 268px each. This should work, but the `SpButton` has `contentPadding = PaddingValues(horizontal = 24.dp)`, so internal padding of 48dp eats into available text space significantly.

### Proposed Design Solution

**Option A (Recommended): Stack play buttons above action buttons**

Split the action row into two rows:
1. **Top row**: Play buttons only (Resume + New Game, or just Play/Download), side by side with `weight(1f)`.
2. **Bottom row**: Icon action buttons (Favorite, Play Later, Add to Collection) in a row, left-aligned.

This gives the play buttons the full width minus padding. The icon buttons are naturally smaller and don't need the full width.

```
Row 1: [     Resume     ] [   New Game    ]
Row 2: [Heart] [Clock] [Library+]
```

If only "Play" or "Download" is shown (no saves), it should fill the full width as a single button.

**Implementation details:**
- Keep `SpSpacing.Medium` (12dp) between buttons in each row
- Keep `SpSpacing.Small` (8dp) between the two rows
- Play buttons: `Modifier.weight(1f)` within their row
- Icon buttons row: `Arrangement.spacedBy(SpSpacing.Medium)`, no weight, natural sizing

### Responsive Behavior
- **Portrait phone/tablet**: Same two-row layout. Plenty of horizontal room for both rows.
- **Landscape handheld**: Benefits most. Play buttons get full right-panel width. Icon buttons are compact.
- **Desktop**: Same layout. The wider panel makes both rows comfortable.

### Edge Cases
- Download state with progress bar: Progress bar stays below both rows as it currently does.
- Netplay button: Stays as a separate full-width row below the icon buttons row.
- When only "Play" is shown (no saves): Single button fills the full row width, icon buttons row below.

---

## Issue 2: Action Button Icons + Responsive Overflow Menu

### Current Problem
The icon-only action buttons (Favorite, Play Later, Add to Collection) at lines 419-468 of `GameDetailScreen.kt` use `SpButton` with `text = ""` and a `leadingIcon`. Looking at `SpButton.kt` line 147-149, the `ButtonContent` composable always renders:
```kotlin
leadingIcon()
Spacer(Modifier.width(SpSpacing.Small))  // 8dp spacer even when text is empty
Text(text = text, ...)                     // Empty text still takes layout space
```

The `Spacer` after the icon and the empty `Text` are causing the icon to not be visually centered - it's pushed left by the trailing spacer and zero-width text.

### Proposed Design Solution

**Fix 1: Center icons in icon-only buttons**

In `SpButton.kt`, modify `ButtonContent` to skip the spacer when text is empty:

```kotlin
@Composable
private fun ButtonContent(
    text: String,
    isLoading: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
    indicatorColor: Color = SpColor.OnPrimary,
) {
    if (isLoading) {
        CircularProgressIndicator(...)
        if (text.isNotEmpty()) Spacer(Modifier.width(SpSpacing.Small))
    } else if (leadingIcon != null) {
        leadingIcon()
        if (text.isNotEmpty()) Spacer(Modifier.width(SpSpacing.Small))
    }
    if (text.isNotEmpty()) {
        Text(text = text, style = SpTypography.LabelLarge)
    }
}
```

Also, when text is empty, use square-ish padding instead of the wide horizontal padding:
- Add a parameter like `isIconOnly` or detect when `text.isEmpty()` and use `PaddingValues(SpSpacing.Medium)` (12dp all around) instead of `PaddingValues(horizontal = SpSpacing.XLarge, vertical = SpSpacing.Medium)` (24dp horizontal, 12dp vertical).

**Fix 2: No overflow menu needed**

With Issue 1's two-row layout, the icon buttons have their own row and aren't competing with play buttons for space. Three icon buttons at ~48dp each + 24dp spacing = ~168dp total, which fits easily on any screen. An overflow menu would hurt discoverability and add unnecessary interaction cost on a handheld.

### Design System Consistency
- `SpIconButton` already exists for circular icon buttons (used in top bars). However, the game detail action buttons have toggle states (Favorite filled/outlined) that `SpIconButton` doesn't support. The approach of fixing `SpButton` for icon-only use is better since it preserves the toggle styling.

---

## Issue 3: Collection Picker - Inline "Create New Collection"

### Current Problem
In `CollectionPickerDialog.kt`, the "Create Collection" button only shows when `collections.isEmpty()` (lines 86-95). When a user has existing collections but wants to create a new one, they have no way to do it from this dialog.

The `onCreateCollection` callback is optional and dismisses the dialog before navigating.

### Proposed Design Solution

Add a "Create New Collection" item as the **first item** in the collection list (above existing collections), always visible when `onCreateCollection != null`:

```
+--------------------------------------+
|  Add to Collection                   |
|                                      |
|  + Create New Collection             |  <-- always visible, primary color
|  ─────────────────────────────────── |
|  My RPGs                   3 games   |
|  ─────────────────────────────────── |
|  Favorites                 12 games  |
|  ─────────────────────────────────── |
|  Completed                 8 games   |
|                                      |
|          [ Cancel ]                  |
+--------------------------------------+
```

**Implementation details:**
- Add a sticky item before the `LazyColumn` items (or as the first item in the list).
- Use a `Row` with:
  - `Icon(Icons.Outlined.Add, ...)` (or `Icons.Filled.Add`) in `SpColor.Primary`, size 20dp
  - `Text("Create New Collection", style = SpTypography.TitleMedium, color = SpColor.Primary)`
- Clickable with same padding as `CollectionPickerItem` for alignment
- Followed by a `HorizontalDivider` separator
- On click: dismiss dialog, invoke `onCreateCollection()`
- Also keep it in the empty state (existing behavior works fine there)

### Responsive Behavior
- Same on all form factors. The dialog already uses `fillMaxWidth(0.85f)` and `heightIn(max = 300.dp)` which works across sizes.

### Edge Cases
- When collections list is empty: Show "Create New Collection" item with explanatory text below it ("You don't have any collections yet"). Remove the standalone button that currently appears in empty state.
- When `onCreateCollection` is null: Don't show the create item (backwards compatible).

---

## Issue 4: Game Details Layout for Landscape

### Current Problem
In `GameDetailLayout.kt` line 55, landscape mode gives the cover art 40% of screen width (`constraintsMaxWidth * 0.4f`). On a small landscape handheld, this is about 512dp on a 1280px screen, which is large relative to the available vertical space. The cover fills `Modifier.fillMaxSize()` within that column (line 84), making it dominate the view.

Meanwhile, the right-side scrollable content gets only 60% width with `contentPadding = PaddingValues(SpSpacing.XLarge)` (24dp all sides), further reducing usable space.

### Proposed Design Solution

**Reduce cover width in landscape for small screens:**

Use `BoxWithConstraints` to check screen height and adjust the cover ratio:

| Screen condition | Cover width ratio | Rationale |
|---|---|---|
| `maxHeight < 500.dp` (small landscape handheld) | 30% | Compact: saves horizontal space for content |
| `maxHeight >= 500.dp` (tablet/desktop landscape) | 40% | Current behavior preserved |

```kotlin
val coverRatio = if (maxHeight < 500.dp) 0.30f else 0.40f
```

**Reduce content padding on small screens:**

```kotlin
val contentPadding = if (maxHeight < 500.dp) {
    PaddingValues(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium)
} else {
    PaddingValues(SpSpacing.XLarge)
}
```

This gives the content area more room. On a 1280px-wide handheld, going from 60% to 70% gives an extra 128px for the content panel.

### Responsive Behavior
- **Portrait (any device)**: No change. Uses `PortraitLayout` as before.
- **Landscape handheld (height < 500dp)**: Cover takes 30% width, content padding is tighter. Content area is ~70% of screen.
- **Landscape tablet/desktop (height >= 500dp)**: Keeps current 40%/60% split and padding.

### Edge Cases
- Very narrow landscape windows on desktop (e.g., user resizes): The `isLandscape = maxWidth > maxHeight` check already handles switching to portrait. If someone makes a very wide but short window, the 30% cover ratio still works well.

---

## Issue 5: Library Header - Hide in Landscape

### Current Problem
In `LibraryScreen.kt`, the `SpTopBar(title = "Library")` is always visible (line 44). The `SpTopBar` is 64dp tall (defined in `SpSpacing.TopBarHeight`) plus status bar inset. On a landscape handheld with ~400dp of usable height, this header + the `TabRow` below it consume approximately 64 + 48 = 112dp, which is ~28% of the screen height before any content is shown.

### Proposed Design Solution

**Merge the title into the TabRow on landscape:**

Use `BoxWithConstraints` to detect orientation and conditionally show/hide the top bar:

```kotlin
BoxWithConstraints {
    val isLandscape = maxWidth > maxHeight

    Column(...) {
        if (!isLandscape) {
            SpTopBar(title = "Library")
        }
        // TabRow stays visible always
        TabRow(...)
        // Content
    }
}
```

This saves 64dp+ of vertical space on landscape. The tabs themselves ("Consoles", "Games", "Favorites", etc.) already make the context clear - you know you're in the Library.

**Alternatively, for even more savings:** On landscape, use a more compact TabRow with reduced height. However, the tab text must remain touchable (min 48dp), so the savings would be marginal. Hiding the title bar is the biggest win.

### Responsive Behavior
- **Portrait phone/tablet**: Full top bar + tab row (current behavior).
- **Landscape handheld**: Tab row only. Saves 64dp+ vertical space.
- **Desktop**: Can follow landscape behavior since horizontal space is abundant and the tab row provides sufficient navigation context.

### Edge Cases
- If the user expects a back button from Library: Library is a root-level screen in the bottom navigation, so there's no back button in `SpTopBar(title = "Library")` anyway. No loss.
- Status bar inset: The `SpTopBar` includes `Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))`. In landscape mode on most handhelds, the status bar is minimal or absent, but to be safe, add the status bar spacer above the TabRow when the top bar is hidden.

---

## Issue 6: Search Field to Toggle Button

### Current Problem
In `ConsoleScreen.kt` lines 102-109, the `SpSearchField` is always visible, taking up approximately 56dp of vertical space (OutlinedTextField default height). On `AllGamesScreen.kt` lines 55-65, same issue. On a landscape handheld, this is valuable real estate.

### Proposed Design Solution

**Replace the always-visible search field with a search icon button in the SpTopBar:**

For `ConsoleScreen`:
```kotlin
SpTopBar(
    title = consoleName,
    showBack = true,
    onBack = onBack,
    actions = {
        SpIconButton(
            icon = Icons.Filled.Search,
            contentDescription = "Search games",
            onClick = { showSearch = !showSearch },
        )
        SpIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Console settings",
            onClick = onNavigateToConsoleSettings,
        )
    },
)

AnimatedVisibility(visible = showSearch) {
    SpSearchField(
        value = state.searchQuery,
        onValueChange = { viewModel.onIntent(GameListIntent.Search(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small),
        placeholder = "Search $consoleName games...",
    )
}
```

**Behavior:**
- Search icon in the top bar toggles an `AnimatedVisibility` search field
- When search is active, the field slides in with a fade/expand animation
- When the user clears the search text AND closes search, reset the filter
- On landscape handhelds, search is hidden by default, saving ~56dp
- The search icon is always accessible with a single tap

**For `AllGamesScreen`:** Same pattern. Since `AllGamesScreen` doesn't have its own `SpTopBar` (it's inside `LibraryScreen`'s tab content), the search toggle should be a compact inline toggle:
- A search icon button at the start of the `GameLibraryControls` row
- When tapped, it expands the search field in place of the filter chips row (or pushes content down with AnimatedVisibility)

### Responsive Behavior
- **All form factors**: Search is behind a toggle. The pattern is familiar from most mobile apps.
- **Desktop**: Could optionally always show the search field since space isn't constrained. Use `BoxWithConstraints`: if `maxWidth > 900.dp`, show search always; otherwise, use the toggle.

### Edge Cases
- Active search with results: If the user closes the search toggle while a query is active, clear the query and reset results.
- Keyboard management: When search opens, request focus on the text field. When closed, clear focus.
- Gamepad navigation: The search icon must be focusable via `spFocusRing`.

---

## Issue 7: Auto-Scrape Visible Games

### Current Problem
Currently, scraping is only triggered when the user opens a game's detail screen (`GameDetailViewModel.kt` line 117-118). If a game hasn't been scraped, its card in the grid shows the placeholder cover (two-letter initials in `CoverPlaceholder`). Users must tap into each game individually to trigger scraping.

The server already has a `POST /api/games/{id}/scrape-if-needed` endpoint that returns immediately and scrapes in the background.

### Proposed Design Solution

**Auto-scrape games that appear in grid/list views, and live-update covers:**

**Step 1: Add a `scrapeIfNeeded` call in the GameListViewModel**

When games are loaded (from `LoadConsoles`, `SelectConsole`, or `Search`), filter for games where `scrapeAttempts == 0` and fire `scrapeIfNeeded` for each (debounced, batched to avoid overwhelming the server).

```kotlin
// In GameListViewModel, after games are loaded:
private fun triggerAutoScrape(games: List<Game>) {
    val unscraped = games.filter { it.scrapeAttempts == 0 }.take(10) // Batch limit
    unscraped.forEach { game ->
        scope.launch(dispatchers.io) {
            try {
                apiClient.scrapeIfNeeded(game.id)
            } catch (_: Exception) { /* silent fail */ }
        }
    }
}
```

**Step 2: Poll for updates and refresh the game list**

After triggering scrapes, poll the API after a few seconds to refresh the game list. The refreshed data will include cover URLs populated by the scraper.

```kotlin
// After triggering scrape, schedule a refresh
if (unscraped.isNotEmpty()) {
    scope.launch(dispatchers.io) {
        delay(3000) // Wait for scraper to work
        // Re-fetch the current game list
        refreshCurrentView()
    }
}
```

**Step 3: Coil image loader handles the rest**

Once the game model updates with a non-null `coverUrl`, the `SpCoverArt` composable will automatically load the new image via Coil's `SubcomposeAsyncImage`. No additional UI work needed - the recomposition from the state update triggers the image load.

### Responsive Behavior
- Same on all form factors. This is a data/network behavior, not a layout concern.

### Edge Cases
- Rate limiting: Limit to 10 scrape requests per batch. Don't re-trigger for the same games if they're already being scraped.
- Error handling: Silent failures. If a scrape fails, the placeholder cover stays. User can still trigger manually from the detail screen.
- Slow connection: The delay-and-refresh approach is simple but may miss slow scrapes. Consider a second retry at 8 seconds.
- Server load: The `scrape-if-needed` endpoint is idempotent and the server handles deduplication. Multiple calls for the same game are safe.

---

## Issue 8: Missing Icons (Circles Visible Instead of Icons)

### Current Problem
The "circles visible instead of icons" near the game title most likely refers to the **console chip and metadata chips** row in `GameDetailScreen.kt` lines 351-361:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
    SpConsoleChip(consoleName = game.consoleName, consoleColor = getConsoleColor(game.consoleName))
    game.genre?.let { SpChip(text = it) }
    game.releaseDate?.let { SpChip(text = it) }
}
```

However, looking more carefully, the reported issue says "circles visible instead of icons." This is most likely the **icon-only action buttons** (Favorite, Play Later, Add to Collection). These buttons use Material Icons from `Icons.Filled` and `Icons.Outlined` packages.

The project dependencies do NOT include `material-icons-extended`. The icons used are:
- `Icons.Filled.Favorite` - in core icons
- `Icons.Outlined.FavoriteBorder` - in core icons
- `Icons.Filled.WatchLater` - **in extended icons**
- `Icons.Outlined.WatchLater` - **in extended icons**
- `Icons.Outlined.LibraryAdd` - **in extended icons**

If the `material-icons-extended` dependency is missing, these icons render as empty/placeholder circles (the default Material icon placeholder).

### Proposed Design Solution

**Option A (Recommended): Add the `material-icons-extended` dependency**

In the shared module's `build.gradle.kts`:
```kotlin
commonMain.dependencies {
    implementation(compose.materialIconsExtended)
}
```

This immediately fixes all missing icons. The downside is APK size increase (~2MB), but this is the standard approach.

**Option B: Replace extended icons with core icons**

If APK size is critical, replace the extended icons with alternatives from the core set:
- `Icons.Filled.WatchLater` -> `Icons.Filled.AccessTime` or use `Icons.Filled.BookmarkAdded`
- `Icons.Outlined.WatchLater` -> `Icons.Outlined.AccessTime`
- `Icons.Outlined.LibraryAdd` -> `Icons.Filled.PlaylistAdd` (core) or `Icons.Filled.Add`

Core set icons are always bundled.

### Verification
Check the project's Gradle files for `materialIconsExtended` or `icons-extended`. If the dependency exists but icons still don't render, it could be a proguard/R8 stripping issue (add keep rules for Material icons).

### Responsive Behavior
- N/A. This is a dependency/asset issue, not a layout issue.

---

## Implementation Priority

Based on user impact and complexity:

1. **Issue 8 (Missing icons)** - Quick fix, high visual impact. Just add dependency or swap icons.
2. **Issue 1 (Squashed buttons)** - High impact on handheld usability. Simple layout restructure.
3. **Issue 2 (Icon centering)** - Quick fix in SpButton, improves visual quality.
4. **Issue 5 (Library header)** - Quick conditional hide, significant space savings.
5. **Issue 4 (Landscape cover size)** - Moderate complexity, good space savings.
6. **Issue 6 (Search toggle)** - Moderate complexity, good space savings.
7. **Issue 3 (Collection picker)** - Small UX improvement, moderate complexity.
8. **Issue 7 (Auto-scrape)** - Backend integration, most complex, lowest urgency.

---

## Design System Notes

### Existing components to reuse
- `SpButton` (with the proposed icon-only fix)
- `SpIconButton` for toolbar icon buttons
- `SpTopBar` for header management
- `SpSearchField` for the expandable search
- `SpChip` / `SpConsoleChip` for metadata display
- `AnimatedVisibility` for search toggle animation
- `BoxWithConstraints` for responsive breakpoints

### New components needed
- None. All changes can be implemented within existing components or as modifications to existing composables.

### Breakpoint convention to establish
For consistent responsive behavior across the app, define breakpoint values in `SpSpacing`:
```kotlin
// Responsive breakpoints
val CompactHeight = 500.dp    // Landscape handheld threshold
val CompactWidth = 600.dp     // Narrow screen threshold
val MediumWidth = 900.dp      // Medium screen threshold
```

These can be used with `BoxWithConstraints` throughout the app for consistent responsive decisions.
