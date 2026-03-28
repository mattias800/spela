# Per-Tab Navigation Stacks

## Problem

The navigation system uses a single flat back stack with a hardcoded
`activeTabForScreen()` mapping to determine which tab is highlighted.
When navigating from Consoles -> Console -> GameDetail, the tab indicator
switches to Home because `GameDetail` isn't mapped to any tab and falls
through to the `else -> HOME` default.

Screens should not "belong" to a tab. Each tab should maintain its own
navigation stack, and the active tab should be tracked explicitly.

## Design

### NavigationState

Replace the single `backStack` and derived tab with explicit per-tab stacks:

```kotlin
data class NavigationState(
    val activeTab: BottomNavTab = BottomNavTab.HOME,
    val tabStacks: Map<BottomNavTab, List<SpScreen>> = defaultTabStacks(),
    val isGoingBack: Boolean = false,
    val isTabSwitch: Boolean = false,
    // overlay fields unchanged
    val showInGameOverlay: Boolean = false,
    // ... existing overlay fields ...
    val tabStacksBehindOverlay: Map<BottomNavTab, List<SpScreen>> = emptyMap(),
    val activeTabBehindOverlay: BottomNavTab? = null,
    // session restore fields unchanged
    val isRestoringSession: Boolean = true,
    val restoredServerUrl: String? = null,
    val isOffline: Boolean = false,
)
```

Each tab stack is a `List<SpScreen>` where the last element is the
visible screen and the first element is the tab root. A helper function
creates the default stacks:

```kotlin
fun defaultTabStacks(): Map<BottomNavTab, List<SpScreen>> = mapOf(
    BottomNavTab.HOME to listOf(SpScreen.Home),
    BottomNavTab.EXPLORE to listOf(SpScreen.Explore),
    BottomNavTab.CONSOLES to listOf(SpScreen.Consoles),
    BottomNavTab.COLLECTIONS to listOf(SpScreen.Collections),
    BottomNavTab.ACTIVITY to listOf(SpScreen.Activity),
    BottomNavTab.SETTINGS to listOf(SpScreen.Settings),
)
```

**Removed fields:** `currentScreen`, `backStack`, `screenBehindOverlay`,
`backStackBehindOverlay`.

**Derived property:** `currentScreen` becomes
`tabStacks[activeTab]!!.last()`.

### Intent Handling

**NavigateTo(screen):**
Push the screen onto the active tab's stack.

```
tabStacks[activeTab] = [...current, screen]
isGoingBack = false
isTabSwitch = false
```

**SwitchTab(tab):**
Change the active tab. All stacks are preserved — the user returns to
where they left off on the target tab.

```
activeTab = tab
isGoingBack = false
isTabSwitch = true
```

**GoBack:**
Pop from the active tab's stack. Three cases:

1. Stack has >1 entry: pop the top, `isGoingBack = true`
2. Stack has 1 entry (the root) and tab is not Home: switch to Home tab,
   `isGoingBack = true`
3. Stack has 1 entry and tab is Home: do nothing (app exit is handled by
   the platform)

```
val stack = tabStacks[activeTab]
if (stack.size > 1) {
    tabStacks[activeTab] = stack.dropLast(1)
    isGoingBack = true
} else if (activeTab != HOME) {
    activeTab = HOME
    isGoingBack = true
}
```

**NextSection / PreviousSection:**
Cycle `activeTab` to the adjacent tab. Stacks are preserved.
`isTabSwitch = false` (gamepad section cycling has no animation).

**ShowOverlay:**
Save `activeTab` and `tabStacks` (the entire navigation state):

```
activeTabBehindOverlay = activeTab
tabStacksBehindOverlay = tabStacks
showInGameOverlay = true
```

**HideOverlay:**
Restore saved state:

```
activeTab = activeTabBehindOverlay
tabStacks = tabStacksBehindOverlay
showInGameOverlay = false
```

### Deleted: activeTabForScreen()

The `activeTabForScreen()` function is deleted from `NavigationViewModel`
and from `SpelaApp`. All call sites use `navState.activeTab` directly.

### SpelaApp Changes

All references to `navState.currentScreen` use the derived property
instead: `navState.currentScreen` (backed by
`tabStacks[activeTab]!!.last()`).

Tab selection in `SpBottomNavBar`, `SpNavigationRail`, and
`SpSectionIndicator`:
- Before: `activeTab = activeTabForScreen(navState.currentScreen)`
- After: `activeTab = navState.activeTab`

The `saveableStateHolder` key logic is unchanged — it uses
`screen.route` which is still unique per screen instance.

Animation detection:
- `isGoingBack` and `isTabSwitch` flags work exactly as before
- No change to `AnimatedContent` transition specs

### Auth / Session Handling

**Auth failure:** Reset all tab stacks to defaults, set
`activeTab = HOME`.

**Session restore:** Sets `activeTab = HOME` with default stacks (same
as today).

**resetDatabase:** Same — clear everything to defaults.

### What Stays the Same

- `SpScreen` sealed class (no changes)
- All screen composables (no changes)
- Animation logic (`isGoingBack`, `isTabSwitch` flags)
- Overlay save/restore pattern (same concept, more state saved)
- `NavigationIntent` sealed interface (no new intents)
- `BottomNavTab` enum

### Back Button Behavior (Android)

1. Pop current tab's stack
2. When only the root remains on a non-Home tab, switch to Home
3. When Home shows only its root, do nothing (platform handles app exit)

This matches Android Navigation component guidelines and iOS tab
controller behavior.

## Files Changed

| File | Change |
|------|--------|
| `SpNavigation.kt` | Replace `backStack`/`currentScreen` with `activeTab`/`tabStacks`, add derived `currentScreen`, add `defaultTabStacks()` |
| `NavigationViewModel.kt` | Rewrite all intent handlers for per-tab stacks, delete `activeTabForScreen()` |
| `SpelaApp.kt` | Replace `activeTabForScreen(navState.currentScreen)` with `navState.activeTab`, update `currentScreen` access |

## Not Changed

- No screen composables are modified
- No new dependencies
- No new intents or state fields beyond what's listed
