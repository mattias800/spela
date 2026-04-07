# Controller Status Indicators Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add always-visible, compact controller status indicators that appear when 2+ controllers are connected, across all navigation layouts.

**Architecture:** Extend `GamepadPortManager` with a derived `StateFlow<ControllerStatusState>` that combines port assignments + activity timestamps. Three UI variants (rail card, pill extension, floating mini-pill) consume this single state. New shared `Sp*` components follow the Design → Content hierarchy.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Coroutines/StateFlow, Koin DI

**Spec:** `docs/superpowers/specs/2026-04-07-controller-status-indicators-design.md`

---

## File Structure

### New Files
| File | Purpose |
|------|---------|
| `player/shared/src/commonMain/kotlin/com/spela/player/libretro/ControllerStatusState.kt` | Data model: `PortStatus`, `ControllerStatusState` |
| `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpControllerDot.kt` | Design component: single dot with 3 visual states + flash animation |
| `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpControllerStatusRow.kt` | Content component: row of dots with P# labels |
| `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpControllerStatusCard.kt` | Content component: card wrapper for rail with "CONTROLLERS" label |
| `player/shared/src/desktopTest/kotlin/com/spela/player/libretro/ControllerStatusStateTest.kt` | Unit tests for state derivation |
| `player/shared/src/desktopTest/kotlin/com/spela/player/desktop/e2e/ControllerStatusIndicatorTest.kt` | Desktop E2E tests for all UI variants |

### Modified Files
| File | Change |
|------|--------|
| `player/shared/src/commonMain/kotlin/com/spela/player/libretro/GamepadPortManager.kt` | Add `controllerStatus: StateFlow<ControllerStatusState>` + coroutine for activity timeout |
| `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpNavigationRail.kt` | Add controller card (labeled) / stacked dots (icon-only) above Settings |
| `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpSectionIndicator.kt` | Append controller dots after R1 when multiplayer |
| `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/SpelaApp.kt` | Read controller status, pass to rail/pill, add floating mini-pill |

---

## Task 1: Data Model — ControllerStatusState

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/libretro/ControllerStatusState.kt`
- Test: `player/shared/src/desktopTest/kotlin/com/spela/player/libretro/ControllerStatusStateTest.kt`

- [ ] **Step 1: Write the data model**

Create the file with the data classes:

```kotlin
package com.spela.player.libretro

/**
 * Status of a single controller port.
 */
data class PortStatus(
    val port: Int,
    val connected: Boolean,
    val active: Boolean,
)

/**
 * UI-ready snapshot of all controller port statuses.
 * Derived from [GamepadPortManager] port assignments and activity timestamps.
 */
data class ControllerStatusState(
    val ports: List<PortStatus>,
    val connectedCount: Int,
    val isMultiplayer: Boolean,
) {
    companion object {
        /** Number of ports shown in the UI (most games support up to 4 players). */
        const val DISPLAY_PORTS = 4

        /** Creates the state from raw port data. */
        fun fromPortData(
            occupiedPorts: BooleanArray,
            lastActivityMs: LongArray,
            nowMs: Long,
            activityTimeoutMs: Long = 300L,
        ): ControllerStatusState {
            val ports = (0 until DISPLAY_PORTS).map { port ->
                val connected = port < occupiedPorts.size && occupiedPorts[port]
                val lastActivity = if (port < lastActivityMs.size) lastActivityMs[port] else 0L
                val active = connected && lastActivity > 0L && (nowMs - lastActivity) < activityTimeoutMs
                PortStatus(port = port, connected = connected, active = active)
            }
            val connectedCount = ports.count { it.connected }
            return ControllerStatusState(
                ports = ports,
                connectedCount = connectedCount,
                isMultiplayer = connectedCount >= 2,
            )
        }

        /** Empty state — no controllers connected. */
        val Empty = ControllerStatusState(
            ports = (0 until DISPLAY_PORTS).map { PortStatus(port = it, connected = false, active = false) },
            connectedCount = 0,
            isMultiplayer = false,
        )
    }
}
```

- [ ] **Step 2: Write unit tests for the state derivation**

```kotlin
package com.spela.player.libretro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControllerStatusStateTest {

    @Test
    fun emptyStateHasNoConnectedPorts() {
        val state = ControllerStatusState.Empty
        assertEquals(4, state.ports.size)
        assertEquals(0, state.connectedCount)
        assertFalse(state.isMultiplayer)
        state.ports.forEach { assertFalse(it.connected); assertFalse(it.active) }
    }

    @Test
    fun singleControllerIsNotMultiplayer() {
        val occupied = BooleanArray(8) { it == 0 }
        val activity = LongArray(8)
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertEquals(1, state.connectedCount)
        assertFalse(state.isMultiplayer)
        assertTrue(state.ports[0].connected)
        assertFalse(state.ports[1].connected)
    }

    @Test
    fun twoControllersIsMultiplayer() {
        val occupied = BooleanArray(8) { it == 0 || it == 1 }
        val activity = LongArray(8)
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertEquals(2, state.connectedCount)
        assertTrue(state.isMultiplayer)
    }

    @Test
    fun recentActivityMarksPortActive() {
        val occupied = BooleanArray(8) { it == 0 }
        val activity = LongArray(8).also { it[0] = 900L }
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertTrue(state.ports[0].active, "Port 0 should be active (100ms ago < 300ms timeout)")
    }

    @Test
    fun expiredActivityMarksPortInactive() {
        val occupied = BooleanArray(8) { it == 0 }
        val activity = LongArray(8).also { it[0] = 500L }
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertFalse(state.ports[0].active, "Port 0 should be inactive (500ms ago >= 300ms timeout)")
    }

    @Test
    fun disconnectedPortIsNeverActive() {
        val occupied = BooleanArray(8) // all false
        val activity = LongArray(8).also { it[0] = 999L }
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertFalse(state.ports[0].active, "Disconnected port should not be active even with recent timestamp")
    }

    @Test
    fun onlyFirstFourPortsAreIncluded() {
        val occupied = BooleanArray(8) { true }
        val activity = LongArray(8)
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertEquals(4, state.ports.size)
        assertEquals(4, state.connectedCount)
    }

    @Test
    fun nonContiguousPortsHandledCorrectly() {
        val occupied = BooleanArray(8) { it == 0 || it == 2 }
        val activity = LongArray(8).also { it[2] = 950L }
        val state = ControllerStatusState.fromPortData(occupied, activity, nowMs = 1000L)
        assertTrue(state.ports[0].connected)
        assertFalse(state.ports[0].active)
        assertFalse(state.ports[1].connected)
        assertTrue(state.ports[2].connected)
        assertTrue(state.ports[2].active)
        assertTrue(state.isMultiplayer)
    }
}
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `cd /Users/mattias800/repos/spela && player/run-desktop-tests.sh`
Expected: All `ControllerStatusStateTest` tests pass.

- [ ] **Step 4: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/libretro/ControllerStatusState.kt \
      player/shared/src/desktopTest/kotlin/com/spela/player/libretro/ControllerStatusStateTest.kt
git commit -m "feat: add ControllerStatusState data model for controller indicators"
```

---

## Task 2: Expose ControllerStatusState from GamepadPortManager

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/libretro/GamepadPortManager.kt`
- Test: `player/shared/src/desktopTest/kotlin/com/spela/player/libretro/GamepadPortManagerTest.kt` (add tests)

- [ ] **Step 1: Write tests for the new StateFlow**

Add these tests to the existing `GamepadPortManagerTest.kt`:

```kotlin
@Test
fun controllerStatusEmptyByDefault() {
    val status = manager.controllerStatus.value
    assertEquals(0, status.connectedCount)
    assertFalse(status.isMultiplayer)
    status.ports.forEach { assertFalse(it.connected) }
}

@Test
fun controllerStatusUpdatesOnConnect() {
    manager.connectDevice(100, "Xbox")
    manager.connectDevice(200, "DualSense")
    val status = manager.controllerStatus.value
    assertEquals(2, status.connectedCount)
    assertTrue(status.isMultiplayer)
    assertTrue(status.ports[0].connected)
    assertTrue(status.ports[1].connected)
    assertFalse(status.ports[2].connected)
}

@Test
fun controllerStatusUpdatesOnDisconnect() {
    manager.connectDevice(100, "Xbox")
    manager.connectDevice(200, "DualSense")
    assertTrue(manager.controllerStatus.value.isMultiplayer)

    manager.disconnectDevice(100)
    val status = manager.controllerStatus.value
    assertEquals(1, status.connectedCount)
    assertFalse(status.isMultiplayer)
    assertFalse(status.ports[0].connected)
    assertTrue(status.ports[1].connected)
}

@Test
fun controllerStatusReflectsActivityAfterReport() {
    manager.connectDevice(100, "Xbox")
    manager.connectDevice(200, "DualSense")
    manager.reportActivity(0)

    // The activity flag depends on the coroutine-driven refresh.
    // After reportActivity, a synchronous snapshot should have the timestamp.
    // The controllerStatus flow is updated on connect/disconnect;
    // activity refresh is handled by the periodic coroutine (tested via E2E).
    val status = manager.controllerStatus.value
    assertTrue(status.ports[0].connected)
    assertTrue(status.ports[1].connected)
}

@Test
fun controllerStatusClearsOnClear() {
    manager.connectDevice(100, "Xbox")
    manager.connectDevice(200, "DualSense")
    assertTrue(manager.controllerStatus.value.isMultiplayer)

    manager.clear()
    val status = manager.controllerStatus.value
    assertEquals(0, status.connectedCount)
    assertFalse(status.isMultiplayer)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/mattias800/repos/spela && player/run-desktop-tests.sh`
Expected: Compilation failure — `controllerStatus` doesn't exist yet on `GamepadPortManager`.

- [ ] **Step 3: Add controllerStatus StateFlow to GamepadPortManager**

In `GamepadPortManager.kt`, add the following:

1. Add import at the top:
```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
```

2. Add a `CoroutineScope` parameter to the constructor:
```kotlin
class GamepadPortManager(
    private val keyMappingRepository: KeyMappingRepository,
    private val scope: CoroutineScope? = null,
)
```

3. Add the new StateFlow and refresh job after the existing `_inputMode` declaration (after line 60):
```kotlin
/** Observable controller status for UI indicators. */
private val _controllerStatus = MutableStateFlow(ControllerStatusState.Empty)
val controllerStatus: StateFlow<ControllerStatusState> = _controllerStatus.asStateFlow()

/** Coroutine that periodically refreshes activity flags. */
private var activityRefreshJob: Job? = null

companion object {
    const val MAX_PORTS = 8
    /** How long a dot stays "active" after the last input. */
    const val ACTIVITY_TIMEOUT_MS = 300L
    /** How often to refresh activity flags. */
    const val ACTIVITY_REFRESH_INTERVAL_MS = 100L
}
```

Note: move `MAX_PORTS` into the companion object update — it's already there, just add the two new constants alongside it.

4. Add a private method to rebuild and emit controller status (below `buildActivityMap()`):
```kotlin
/** Rebuilds and emits the controller status snapshot. */
private fun emitControllerStatus() {
    val now = Clock.System.now().toEpochMilliseconds()
    _controllerStatus.value = ControllerStatusState.fromPortData(
        occupiedPorts = occupiedPorts.copyOf(),
        lastActivityMs = lastActivityMs.copyOf(),
        nowMs = now,
        activityTimeoutMs = ACTIVITY_TIMEOUT_MS,
    )
}
```

5. Call `emitControllerStatus()` at the end of `connectDevice()` (after `_assignments.value = ...`):
```kotlin
_assignments.value = deviceToPort.values.toList()
emitControllerStatus()
startActivityRefreshIfNeeded()
return port
```

6. Call `emitControllerStatus()` at the end of `disconnectDevice()` (after `_portActivity.value = ...`):
```kotlin
_portActivity.value = buildActivityMap()
emitControllerStatus()
stopActivityRefreshIfNotNeeded()
```

7. Call `emitControllerStatus()` at the end of `clear()` (after `_portActivity.value = ...`):
```kotlin
_portActivity.value = emptyMap()
emitControllerStatus()
stopActivityRefresh()
```

8. Call `emitControllerStatus()` at the end of `reportActivity()` (after `_portActivity.value = ...`):
```kotlin
_portActivity.value = buildActivityMap()
emitControllerStatus()
```

9. Add activity refresh coroutine management:
```kotlin
/** Starts the periodic activity refresh if multiplayer and not already running. */
private fun startActivityRefreshIfNeeded() {
    if (activityRefreshJob?.isActive == true) return
    if (deviceToPort.size < 2) return
    val currentScope = scope ?: return
    activityRefreshJob = currentScope.launch {
        while (isActive) {
            delay(ACTIVITY_REFRESH_INTERVAL_MS)
            synchronized(this@GamepadPortManager) {
                emitControllerStatus()
            }
        }
    }
}

/** Stops the refresh if we're no longer in multiplayer. */
private fun stopActivityRefreshIfNotNeeded() {
    if (deviceToPort.size >= 2) return
    stopActivityRefresh()
}

/** Cancels the refresh coroutine. */
private fun stopActivityRefresh() {
    activityRefreshJob?.cancel()
    activityRefreshJob = null
}
```

- [ ] **Step 4: Update Koin module to pass CoroutineScope**

In `player/shared/src/commonMain/kotlin/com/spela/player/di/CommonModule.kt`, update line 75:

Change:
```kotlin
single { GamepadPortManager(get()) }
```
To:
```kotlin
single { GamepadPortManager(get(), get()) }
```

The second `get()` resolves the existing `CoroutineScope` singleton that's already registered in the module.

- [ ] **Step 5: Update SpelaTestHarness**

In `player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/SpelaTestHarness.kt`, find where `gamepadPortManager` is created (around line 165-166):

Change:
```kotlin
val gamepadPortManager = GamepadPortManager(keyMappingRepo)
```
To:
```kotlin
val gamepadPortManager = GamepadPortManager(keyMappingRepo, scope)
```

This passes the test harness's `CoroutineScope` so the activity refresh coroutine runs on the test dispatcher.

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd /Users/mattias800/repos/spela && player/run-desktop-tests.sh`
Expected: All tests pass, including the new `controllerStatus` tests and all existing tests.

- [ ] **Step 7: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/libretro/GamepadPortManager.kt \
      player/shared/src/commonMain/kotlin/com/spela/player/di/CommonModule.kt \
      player/shared/src/desktopTest/kotlin/com/spela/player/libretro/GamepadPortManagerTest.kt \
      player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/SpelaTestHarness.kt
git commit -m "feat: expose ControllerStatusState StateFlow from GamepadPortManager"
```

---

## Task 3: SpControllerDot — Design Component

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpControllerDot.kt`

- [ ] **Step 1: Create the component**

```kotlin
package com.spela.player.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor

/**
 * A single controller status dot.
 *
 * Three visual states:
 * - **Disconnected** (`connected = false`): hollow ring, dim
 * - **Connected idle** (`connected = true, active = false`): solid green, subtle glow
 * - **Active input** (`connected = true, active = true`): white, bright glow — fades back to green
 *
 * This is a design-layer component. No labels, no outer spacing.
 */
@Composable
fun SpControllerDot(
    connected: Boolean,
    active: Boolean,
    port: Int,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val connectedColor = Color(0xFF4ADE80) // green-400
    val activeColor = Color.White
    val disconnectedColor = SpColor.OnBackgroundTertiary.copy(alpha = 0.3f)
    val disconnectedBorderColor = SpColor.OnBackgroundTertiary.copy(alpha = 0.5f)

    val dotColor by animateColorAsState(
        targetValue = when {
            active -> activeColor
            connected -> connectedColor
            else -> disconnectedColor
        },
        animationSpec = tween(durationMillis = if (active) 50 else 300),
        label = "dotColor",
    )

    val glowColor = when {
        active -> Color.White.copy(alpha = 0.4f)
        connected -> connectedColor.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val description = when {
        active -> "Player ${port + 1} active"
        connected -> "Player ${port + 1} connected"
        else -> "Player ${port + 1} not connected"
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (!connected) {
                    // Hollow ring for disconnected
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .drawBehind {
                            drawCircle(
                                color = disconnectedBorderColor,
                                radius = this.size.minDimension / 2,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.5.dp.toPx(),
                                ),
                            )
                        }
                } else {
                    // Solid circle with glow for connected/active
                    Modifier
                        .drawBehind {
                            // Glow
                            drawCircle(
                                color = glowColor,
                                radius = this.size.minDimension * 0.9f,
                            )
                        }
                        .clip(CircleShape)
                        .background(dotColor)
                }
            )
            .semantics { contentDescription = description },
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpControllerDot.kt
git commit -m "feat: add SpControllerDot design component"
```

---

## Task 4: SpControllerStatusRow — Content Component

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpControllerStatusRow.kt`

- [ ] **Step 1: Create the component**

```kotlin
package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.libretro.PortStatus
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * A row of controller dots with player labels (P1, P2, etc.).
 *
 * Content-layer component that composes [SpControllerDot] instances.
 *
 * @param ports The list of port statuses to display.
 * @param showEmptySlots If true, shows all ports (connected + disconnected).
 *   If false, only shows connected ports.
 * @param dotSize Size of each dot.
 */
@Composable
fun SpControllerStatusRow(
    ports: List<PortStatus>,
    showEmptySlots: Boolean,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    spacing: Dp = SpSpacing.Small,
) {
    val visiblePorts = if (showEmptySlots) ports else ports.filter { it.connected }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visiblePorts.forEach { port ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
            ) {
                SpControllerDot(
                    connected = port.connected,
                    active = port.active,
                    port = port.port,
                    size = dotSize,
                )
                Text(
                    text = "P${port.port + 1}",
                    style = SpTypography.LabelSmall,
                    color = if (port.connected) SpColor.OnBackgroundSecondary else SpColor.OnBackgroundTertiary.copy(alpha = 0.5f),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpControllerStatusRow.kt
git commit -m "feat: add SpControllerStatusRow content component"
```

---

## Task 5: SpControllerStatusCard — Rail Card Component

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpControllerStatusCard.kt`

- [ ] **Step 1: Create the component**

```kotlin
package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.libretro.PortStatus
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Card showing controller status for the navigation rail.
 *
 * Displays a "CONTROLLERS" label and a [SpControllerStatusRow] with all 4 slots.
 * Clickable — navigates to controller settings. Focusable for gamepad navigation.
 *
 * @param ports The list of port statuses to display.
 * @param onClick Called when the card is clicked or selected.
 */
@Composable
fun SpControllerStatusCard(
    ports: List<PortStatus>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    val bgAlpha = if (isFocused) 0.1f else 0.05f
    val borderAlpha = if (isFocused) 0.15f else 0.08f

    val connectedCount = ports.count { it.connected }

    Column(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = bgAlpha))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = shape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "$connectedCount controllers connected"
                role = Role.Button
            },
    ) {
        Text(
            text = "CONTROLLERS",
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
            letterSpacing = SpTypography.LabelSmall.letterSpacing,
        )

        SpControllerStatusRow(
            ports = ports,
            showEmptySlots = true,
            modifier = Modifier.padding(top = SpSpacing.XSmall),
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpControllerStatusCard.kt
git commit -m "feat: add SpControllerStatusCard for navigation rail"
```

---

## Task 6: Integrate into SpNavigationRail

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpNavigationRail.kt`

- [ ] **Step 1: Add the controller status parameter and imports**

Add imports at the top of the file:
```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import com.spela.player.libretro.ControllerStatusState
```

Update the function signature to accept controller status:
```kotlin
@Composable
fun SpNavigationRail(
    activeTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    showLabels: Boolean,
    controllerStatus: ControllerStatusState = ControllerStatusState.Empty,
    onControllerStatusClick: () -> Unit = {},
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: Add the indicator between the Spacer and Settings tab**

Replace the section around line 92 (`Spacer(Modifier.weight(1f))`) through the Settings `RailItem`. The existing code:

```kotlin
// Push Settings to the bottom
Spacer(Modifier.weight(1f))

// Settings tab
RailItem(
    tab = BottomNavTab.SETTINGS,
    isSelected = BottomNavTab.SETTINGS == activeTab,
    showLabel = showLabels,
    onClick = { onTabSelected(BottomNavTab.SETTINGS) },
)
```

Replace with:

```kotlin
// Push Settings + controller indicator to the bottom
Spacer(Modifier.weight(1f))

// Controller status indicator (only when 2+ controllers)
val animationsEnabled = LocalAnimationsEnabled.current
AnimatedVisibility(
    visible = controllerStatus.isMultiplayer,
    enter = if (animationsEnabled) fadeIn() + slideInVertically(initialOffsetY = { it }) else EnterTransition.None,
    exit = if (animationsEnabled) fadeOut() + slideOutVertically(targetOffsetY = { it }) else ExitTransition.None,
) {
    if (showLabels) {
        // Labeled rail: full card
        SpControllerStatusCard(
            ports = controllerStatus.ports,
            onClick = onControllerStatusClick,
            modifier = Modifier.padding(horizontal = SpSpacing.Small, bottom = SpSpacing.Small),
        )
    } else {
        // Icon-only rail: stacked dots, no labels, no card
        Column(
            modifier = Modifier
                .padding(bottom = SpSpacing.Small)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onControllerStatusClick() }
                .focusable()
                .padding(vertical = SpSpacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
        ) {
            controllerStatus.ports.filter { it.connected }.forEach { port ->
                SpControllerDot(
                    connected = port.connected,
                    active = port.active,
                    port = port.port,
                    size = 10.dp,
                )
            }
        }
    }
}

// Settings tab
RailItem(
    tab = BottomNavTab.SETTINGS,
    isSelected = BottomNavTab.SETTINGS == activeTab,
    showLabel = showLabels,
    onClick = { onTabSelected(BottomNavTab.SETTINGS) },
)
```

- [ ] **Step 3: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpNavigationRail.kt
git commit -m "feat: add controller status indicator to navigation rail"
```

---

## Task 7: Integrate into SpSectionIndicator (Pill Extension)

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpSectionIndicator.kt`

- [ ] **Step 1: Add parameter and imports**

Add imports:
```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import com.spela.player.libretro.ControllerStatusState
```

Update the function signature:
```kotlin
@Composable
fun SpSectionIndicator(
    activeTab: BottomNavTab,
    visible: Boolean,
    controllerStatus: ControllerStatusState = ControllerStatusState.Empty,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: Add controller dots after R1**

After the R1 `Text` composable (line 72-76), add the controller dots section:

```kotlin
Text(
    text = "R1",
    color = SpColor.OnBackgroundSecondary,
    fontSize = 12.sp,
)

// Controller status dots (only when multiplayer)
if (controllerStatus.isMultiplayer) {
    // Vertical separator
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(Color.White.copy(alpha = 0.15f)),
    )

    SpControllerStatusRow(
        ports = controllerStatus.ports,
        showEmptySlots = false,
        dotSize = 8.dp,
        spacing = SpSpacing.Small,
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/components/SpSectionIndicator.kt
git commit -m "feat: add controller dots to section indicator pill"
```

---

## Task 8: Integrate into SpelaApp (Wire Everything + Floating Mini-Pill)

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/SpelaApp.kt`

- [ ] **Step 1: Collect controller status state**

Near the existing `gamepadPortManager` usage (around line 215-217 where `inputMode` is collected), add:

```kotlin
val controllerStatus by gamepadPortManager?.controllerStatus?.collectAsState()
    ?: remember { mutableStateOf(ControllerStatusState.Empty) }
```

Add import at the top:
```kotlin
import com.spela.player.libretro.ControllerStatusState
```

- [ ] **Step 2: Pass controller status to SpNavigationRail**

Find the `SpNavigationRail` call (around line 340-356) and add the new parameters:

```kotlin
SpNavigationRail(
    activeTab = navState.activeTab,
    onTabSelected = { tab ->
        val targetScreen = when (tab) {
            BottomNavTab.HOME -> SpScreen.Home
            BottomNavTab.EXPLORE -> SpScreen.Explore
            BottomNavTab.CONSOLES -> SpScreen.Consoles
            BottomNavTab.COLLECTIONS -> SpScreen.Collections
            BottomNavTab.ACTIVITY -> SpScreen.Activity
            BottomNavTab.SETTINGS -> SpScreen.Settings
        }
        navigationViewModel.onIntent(
            NavigationIntent.SwitchTab(targetScreen)
        )
    },
    showLabels = navLayoutMode == NavigationLayoutMode.LABELED_RAIL,
    controllerStatus = controllerStatus,
    onControllerStatusClick = {
        navigationViewModel.onIntent(
            NavigationIntent.SwitchTab(SpScreen.Settings)
        )
    },
)
```

- [ ] **Step 3: Pass controller status to SpSectionIndicator**

Find the `SpSectionIndicator` call (around line 1680) and add the new parameter:

```kotlin
SpSectionIndicator(
    activeTab = navState.activeTab,
    visible = sectionIndicatorVisible,
    controllerStatus = controllerStatus,
    modifier = Modifier.padding(top = SpSpacing.Default),
)
```

- [ ] **Step 4: Add the floating mini-pill for phone layout**

After the `SpSectionIndicator` block (around line 1684), add the floating mini-pill:

```kotlin
// Floating controller mini-pill (phone layout, 2+ controllers, no gamepad pill visible)
if (showNavArea && !isGamepadMode && navLayoutMode == NavigationLayoutMode.BOTTOM_BAR && controllerStatus.isMultiplayer) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val animationsEnabled = LocalAnimationsEnabled.current
        AnimatedVisibility(
            visible = true,
            enter = if (animationsEnabled) fadeIn() + slideInVertically(initialOffsetY = { -it }) else EnterTransition.None,
            exit = if (animationsEnabled) fadeOut() + slideOutVertically(targetOffsetY = { -it }) else ExitTransition.None,
        ) {
            Row(
                modifier = Modifier
                    .padding(top = SpSpacing.Default)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpControllerStatusRow(
                    ports = controllerStatus.ports,
                    showEmptySlots = false,
                    dotSize = 8.dp,
                    spacing = SpSpacing.Small,
                )
            }
        }
    }
}
```

Add any missing imports at the top of SpelaApp.kt:
```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
```

(Some of these may already be imported — only add the missing ones.)

- [ ] **Step 5: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/SpelaApp.kt
git commit -m "feat: wire controller status to rail, pill, and floating mini-pill"
```

---

## Task 9: Desktop E2E Tests

**Files:**
- Create: `player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/ControllerStatusIndicatorTest.kt`

- [ ] **Step 1: Write the E2E test file**

```kotlin
package com.spela.player.desktop.e2e

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlin.test.Test
import kotlin.test.assertEquals

class ControllerStatusIndicatorTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(kotlinx.coroutines.test.StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // ---- Visibility ----

    @Test
    fun hiddenWithNoControllers() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        onNodeWithContentDescription("0 controllers connected").assertDoesNotExist()
    }

    @Test
    fun hiddenWithOneController() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        advance(harness)

        onNodeWithContentDescription("1 controllers connected").assertDoesNotExist()
    }

    @Test
    fun visibleWithTwoControllers() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        onNodeWithContentDescription("2 controllers connected").assertIsDisplayed()
    }

    @Test
    fun disappearsWhenDroppingToOneController() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        onNodeWithContentDescription("2 controllers connected").assertIsDisplayed()

        harness.gamepadPortManager.disconnectDevice(2)
        advance(harness)

        onNodeWithContentDescription("2 controllers connected").assertDoesNotExist()
        onNodeWithContentDescription("1 controllers connected").assertDoesNotExist()
    }

    // ---- Dot States ----

    @Test
    fun connectedDotsShowCorrectState() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 3 not connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 4 not connected").assertIsDisplayed()
    }

    @Test
    fun activeInputChangesDoState() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        harness.gamepadPortManager.reportActivity(0)
        advanceQuick(harness)

        onNodeWithContentDescription("Player 1 active").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
    }

    // ---- Navigation ----

    @Test
    fun clickingCardNavigatesToSettings() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        onNodeWithContentDescription("2 controllers connected").performClick()
        advance(harness)

        assertEquals(
            SpScreen.Settings.route,
            harness.navigationViewModel.navigationState.value.currentScreen.route,
        )
    }

    // ---- Disconnect/Reconnect ----

    @Test
    fun disconnectedPortShowsAsNotConnected() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        harness.gamepadPortManager.connectDevice(3, "Pro Controller")
        advance(harness)

        onNodeWithContentDescription("3 controllers connected").assertIsDisplayed()

        // Disconnect P1
        harness.gamepadPortManager.disconnectDevice(1)
        advance(harness)

        // P1 is now empty, P2 and P3 stay
        onNodeWithContentDescription("2 controllers connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 1 not connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 3 connected").assertIsDisplayed()
    }

    @Test
    fun reconnectedControllerReclaimsPort() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        harness.gamepadPortManager.connectDevice(1, "Xbox Controller")
        harness.gamepadPortManager.connectDevice(2, "DualSense")
        advance(harness)

        // Disconnect P1
        harness.gamepadPortManager.disconnectDevice(1)
        advance(harness)

        // Reconnect (may get new device ID)
        harness.gamepadPortManager.connectDevice(99, "Xbox Controller")
        advance(harness)

        // Port 0 is reclaimed
        onNodeWithContentDescription("2 controllers connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 1 connected").assertIsDisplayed()
        onNodeWithContentDescription("Player 2 connected").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the full test suite**

Run: `cd /Users/mattias800/repos/spela && player/run-desktop-tests.sh`
Expected: All tests pass — both the new `ControllerStatusIndicatorTest` tests and all existing tests. Zero regressions.

- [ ] **Step 3: Commit**

```bash
git add player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/ControllerStatusIndicatorTest.kt
git commit -m "test: add desktop E2E tests for controller status indicators"
```

---

## Task 10: Final Verification

- [ ] **Step 1: Run the full desktop test suite one final time**

Run: `cd /Users/mattias800/repos/spela && player/run-desktop-tests.sh`
Expected: All tests pass. Zero regressions.

- [ ] **Step 2: Verify no compile errors across all targets**

Run: `cd /Users/mattias800/repos/spela/player && ./gradlew compileKotlinDesktop compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL for both targets.

- [ ] **Step 3: Spot-check the feature manually**

Launch the desktop app, connect 2+ controllers, and verify:
- The card appears in the rail above Settings
- Dots are green for connected, hollow for empty
- Pressing buttons on a controller flashes the corresponding dot white
- Clicking the card navigates to Settings
- Disconnecting a controller updates the dots
- Disconnecting down to 1 controller hides the card
