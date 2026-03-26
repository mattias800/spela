# Second Screen Keyboard & Trackpad Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add keyboard and trackpad input tabs to the secondary screen companion's Controls page, enabling proper input for platforms that need keyboard (DOS, Amiga, C64) and mouse (ScummVM, RTS games).

**Architecture:** Extend the existing Controls page with a segmented control (Gamepad | Keyboard | Trackpad). Add mouse relative input and keyboard key state to the native C bridge. Persist selected tab per console using the existing DeviceSettingEntity key-value store.

**Tech Stack:** Kotlin Multiplatform (Compose), C (libretro native bridge), SQLDelight, Koin DI

---

### Task 1: Native C — Add mouse relative input and keyboard state

**Files:**
- Modify: `player/native/src/libretro.h`
- Modify: `player/native/src/libretro_input.c`
- Modify: `player/native/src/libretro_bridge.h`

- [ ] **Step 1: Add RETROK keyboard constants and mouse middle button to libretro.h**

Add after the pointer device IDs (after line 96):

```c
/* Mouse device IDs (extended) */
#define RETRO_DEVICE_ID_MOUSE_MIDDLE        4
#define RETRO_DEVICE_ID_MOUSE_WHEELUP       5
#define RETRO_DEVICE_ID_MOUSE_WHEELDOWN     6

/* Keyboard key IDs (RETROK enum).
 * Values match ASCII where applicable. */
enum retro_key {
    RETROK_UNKNOWN        = 0,
    RETROK_BACKSPACE      = 8,
    RETROK_TAB            = 9,
    RETROK_RETURN         = 13,
    RETROK_ESCAPE         = 27,
    RETROK_SPACE          = 32,
    RETROK_EXCLAIM        = 33,
    RETROK_QUOTEDBL       = 34,
    RETROK_HASH           = 35,
    RETROK_DOLLAR         = 36,
    RETROK_AMPERSAND      = 38,
    RETROK_QUOTE          = 39,
    RETROK_LEFTPAREN      = 40,
    RETROK_RIGHTPAREN     = 41,
    RETROK_ASTERISK       = 42,
    RETROK_PLUS           = 43,
    RETROK_COMMA          = 44,
    RETROK_MINUS          = 45,
    RETROK_PERIOD         = 46,
    RETROK_SLASH          = 47,
    RETROK_0              = 48,
    RETROK_1              = 49,
    RETROK_2              = 50,
    RETROK_3              = 51,
    RETROK_4              = 52,
    RETROK_5              = 53,
    RETROK_6              = 54,
    RETROK_7              = 55,
    RETROK_8              = 56,
    RETROK_9              = 57,
    RETROK_COLON          = 58,
    RETROK_SEMICOLON      = 59,
    RETROK_LESS           = 60,
    RETROK_EQUALS         = 61,
    RETROK_GREATER        = 62,
    RETROK_QUESTION       = 63,
    RETROK_AT             = 64,
    RETROK_LEFTBRACKET    = 91,
    RETROK_BACKSLASH      = 92,
    RETROK_RIGHTBRACKET   = 93,
    RETROK_CARET          = 94,
    RETROK_UNDERSCORE     = 95,
    RETROK_BACKQUOTE      = 96,
    RETROK_a              = 97,
    RETROK_b              = 98,
    RETROK_c              = 99,
    RETROK_d              = 100,
    RETROK_e              = 101,
    RETROK_f              = 102,
    RETROK_g              = 103,
    RETROK_h              = 104,
    RETROK_i              = 105,
    RETROK_j              = 106,
    RETROK_k              = 107,
    RETROK_l              = 108,
    RETROK_m              = 109,
    RETROK_n              = 110,
    RETROK_o              = 111,
    RETROK_p              = 112,
    RETROK_q              = 113,
    RETROK_r              = 114,
    RETROK_s              = 115,
    RETROK_t              = 116,
    RETROK_u              = 117,
    RETROK_v              = 118,
    RETROK_w              = 119,
    RETROK_x              = 120,
    RETROK_y              = 121,
    RETROK_z              = 122,
    RETROK_LEFTBRACE      = 123,
    RETROK_BAR            = 124,
    RETROK_RIGHTBRACE     = 125,
    RETROK_TILDE          = 126,
    RETROK_DELETE         = 127,
    RETROK_KP0            = 256,
    RETROK_KP1            = 257,
    RETROK_KP2            = 258,
    RETROK_KP3            = 259,
    RETROK_KP4            = 260,
    RETROK_KP5            = 261,
    RETROK_KP6            = 262,
    RETROK_KP7            = 263,
    RETROK_KP8            = 264,
    RETROK_KP9            = 265,
    RETROK_KP_PERIOD      = 266,
    RETROK_KP_DIVIDE      = 267,
    RETROK_KP_MULTIPLY    = 268,
    RETROK_KP_MINUS       = 269,
    RETROK_KP_PLUS        = 270,
    RETROK_KP_ENTER       = 271,
    RETROK_KP_EQUALS      = 272,
    RETROK_UP             = 273,
    RETROK_DOWN           = 274,
    RETROK_RIGHT          = 275,
    RETROK_LEFT           = 276,
    RETROK_INSERT         = 277,
    RETROK_HOME           = 278,
    RETROK_END            = 279,
    RETROK_PAGEUP         = 280,
    RETROK_PAGEDOWN       = 281,
    RETROK_F1             = 282,
    RETROK_F2             = 283,
    RETROK_F3             = 284,
    RETROK_F4             = 285,
    RETROK_F5             = 286,
    RETROK_F6             = 287,
    RETROK_F7             = 288,
    RETROK_F8             = 289,
    RETROK_F9             = 290,
    RETROK_F10            = 291,
    RETROK_F11            = 292,
    RETROK_F12            = 293,
    RETROK_F13            = 294,
    RETROK_F14            = 295,
    RETROK_F15            = 296,
    RETROK_NUMLOCK        = 300,
    RETROK_CAPSLOCK       = 301,
    RETROK_SCROLLOCK      = 302,
    RETROK_RSHIFT         = 303,
    RETROK_LSHIFT         = 304,
    RETROK_RCTRL          = 305,
    RETROK_LCTRL          = 306,
    RETROK_RALT           = 307,
    RETROK_LALT           = 308,
    RETROK_RMETA          = 309,
    RETROK_LMETA          = 310,
    RETROK_LSUPER         = 311,
    RETROK_RSUPER         = 312,
    RETROK_MODE           = 313,
    RETROK_HELP           = 315,
    RETROK_PRINT          = 316,
    RETROK_SYSREQ         = 317,
    RETROK_BREAK          = 318,
    RETROK_POWER          = 320,
    RETROK_LAST           = 321,
};
```

- [ ] **Step 2: Add mouse and keyboard state to libretro_input.c**

Add after the existing `input_state` struct (replace lines 15-30):

```c
#define MAX_KEYBOARD_KEYS 322  /* RETROK_LAST + 1 */

static struct {
    /* Digital button state per port */
    bool buttons[MAX_PORTS][MAX_BUTTONS];

    /* Analog axis state per port, per stick (left/right), per axis (x/y) */
    int16_t analog[MAX_PORTS][2][2];

    /* Pointer/touch state per port (for RETRO_DEVICE_POINTER) */
    struct {
        int16_t x;       /* -0x7FFF to 0x7FFF */
        int16_t y;       /* -0x7FFF to 0x7FFF */
        bool pressed;
    } pointer[MAX_PORTS];

    /* Mouse state per port (relative deltas, consumed each frame) */
    struct {
        int16_t dx;       /* Accumulated X delta since last poll */
        int16_t dy;       /* Accumulated Y delta since last poll */
        bool left;
        bool right;
    } mouse[MAX_PORTS];

    /* Keyboard state (global, not per-port) */
    bool keyboard[MAX_KEYBOARD_KEYS];

    bool initialized;
} input_state = {0};
```

- [ ] **Step 3: Update input_state_callback for proper mouse relative deltas**

Replace the RETRO_DEVICE_MOUSE case (lines 68-82):

```c
case RETRO_DEVICE_MOUSE: {
    switch (id) {
        case RETRO_DEVICE_ID_MOUSE_X: {
            int16_t dx = input_state.mouse[port].dx;
            input_state.mouse[port].dx = 0;  /* Consumed */
            return dx;
        }
        case RETRO_DEVICE_ID_MOUSE_Y: {
            int16_t dy = input_state.mouse[port].dy;
            input_state.mouse[port].dy = 0;  /* Consumed */
            return dy;
        }
        case RETRO_DEVICE_ID_MOUSE_LEFT:
            return input_state.mouse[port].left ? 1 : 0;
        case RETRO_DEVICE_ID_MOUSE_RIGHT:
            return input_state.mouse[port].right ? 1 : 0;
    }
    break;
}
```

- [ ] **Step 4: Add RETRO_DEVICE_KEYBOARD case to input_state_callback**

Add after the RETRO_DEVICE_POINTER case (before `default:`):

```c
case RETRO_DEVICE_KEYBOARD:
    if (id < MAX_KEYBOARD_KEYS) {
        return input_state.keyboard[id] ? 1 : 0;
    }
    break;
```

- [ ] **Step 5: Add input_set_mouse and input_set_keyboard functions**

Add after `input_set_pointer()`:

```c
/* Called from Kotlin/JNI to set mouse relative movement and button state */
void input_set_mouse(unsigned port, int16_t dx, int16_t dy, bool left, bool right) {
    if (port < MAX_PORTS) {
        input_state.mouse[port].dx += dx;   /* Accumulate deltas */
        input_state.mouse[port].dy += dy;
        input_state.mouse[port].left = left;
        input_state.mouse[port].right = right;
    }
}

/* Called from Kotlin/JNI to set keyboard key state */
void input_set_keyboard(unsigned key, bool pressed) {
    if (key < MAX_KEYBOARD_KEYS) {
        input_state.keyboard[key] = pressed;
    }
}
```

- [ ] **Step 6: Add declarations to libretro_bridge.h**

Add after `input_set_pointer` declaration:

```c
void input_set_mouse(unsigned port, int16_t dx, int16_t dy, bool left, bool right);
void input_set_keyboard(unsigned key, bool pressed);
```

- [ ] **Step 7: Commit**

```bash
git add player/native/src/libretro.h player/native/src/libretro_input.c player/native/src/libretro_bridge.h
git commit -m "feat: add mouse relative input and keyboard state to native bridge"
```

---

### Task 2: Native C — Update device capabilities and add JNI functions

**Files:**
- Modify: `player/native/src/libretro_bridge.c`

- [ ] **Step 1: Update input device capabilities**

In `libretro_bridge.c`, find the `RETRO_ENVIRONMENT_GET_INPUT_DEVICE_CAPABILITIES` case and update:

```c
case RETRO_ENVIRONMENT_GET_INPUT_DEVICE_CAPABILITIES: {
    *(uint64_t *)data = (1 << RETRO_DEVICE_JOYPAD) |
                        (1 << RETRO_DEVICE_ANALOG) |
                        (1 << RETRO_DEVICE_MOUSE) |
                        (1 << RETRO_DEVICE_KEYBOARD) |
                        (1 << RETRO_DEVICE_POINTER);
    return true;
}
```

- [ ] **Step 2: Add JNI functions for mouse and keyboard**

Add after the existing `nativeSetInputPointer` JNI function:

```c
JNI_FUNC(void, nativeSetInputMouse)(JNIEnv *env, jobject thiz,
                                     jint port, jshort dx, jshort dy,
                                     jboolean left, jboolean right) {
    input_set_mouse((unsigned)port, (int16_t)dx, (int16_t)dy,
                    left == JNI_TRUE, right == JNI_TRUE);
}

JNI_FUNC(void, nativeSetInputKeyboard)(JNIEnv *env, jobject thiz,
                                        jint key, jboolean pressed) {
    input_set_keyboard((unsigned)key, pressed == JNI_TRUE);
}

JNI_FUNC(void, nativeSetControllerPortDevice)(JNIEnv *env, jobject thiz,
                                               jint port, jint device) {
    if (g_core.retro_set_controller_port_device) {
        g_core.retro_set_controller_port_device((unsigned)port, (unsigned)device);
        LOGI("Set controller port %d device to %d", port, device);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add player/native/src/libretro_bridge.c
git commit -m "feat: add JNI functions for mouse, keyboard, and device port switching"
```

---

### Task 3: Kotlin JNI bindings

**Files:**
- Modify: `player/shared/src/androidMain/kotlin/com/spela/player/libretro/LibretroJni.kt`
- Modify: `player/shared/src/desktopMain/kotlin/com/spela/player/libretro/LibretroJni.kt`

- [ ] **Step 1: Add external function declarations to Android LibretroJni**

Add after the existing `nativeSetInputPointer` declaration:

```kotlin
external fun nativeSetInputMouse(port: Int, dx: Short, dy: Short, left: Boolean, right: Boolean)
external fun nativeSetInputKeyboard(key: Int, pressed: Boolean)
external fun nativeSetControllerPortDevice(port: Int, device: Int)
```

- [ ] **Step 2: Add matching declarations to Desktop LibretroJni**

Same three declarations added to the desktop variant.

- [ ] **Step 3: Commit**

```bash
git add player/shared/src/androidMain/kotlin/com/spela/player/libretro/LibretroJni.kt \
       player/shared/src/desktopMain/kotlin/com/spela/player/libretro/LibretroJni.kt
git commit -m "feat: add Kotlin JNI declarations for mouse, keyboard, and port device"
```

---

### Task 4: Extend LibretroController interface

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt` (LibretroController interface)
- Modify: `player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/TestFakes.kt` (FakeLibretroController)

- [ ] **Step 1: Add mouse, keyboard, and port device methods to LibretroController**

Add to the interface (after `setPointer`):

```kotlin
fun setMouse(port: Int, dx: Short, dy: Short, left: Boolean, right: Boolean) {}
fun setKeyboardKey(key: Int, pressed: Boolean) {}
fun setControllerPortDevice(port: Int, device: Int) {}
```

- [ ] **Step 2: Add tracking fields to FakeLibretroController**

```kotlin
var lastMouseDx: Short = 0
var lastMouseDy: Short = 0
var lastMouseLeft: Boolean = false
var lastMouseRight: Boolean = false
val keyboardState = mutableMapOf<Int, Boolean>()
var lastPortDevice: Pair<Int, Int>? = null

override fun setMouse(port: Int, dx: Short, dy: Short, left: Boolean, right: Boolean) {
    lastMouseDx = dx; lastMouseDy = dy; lastMouseLeft = left; lastMouseRight = right
}
override fun setKeyboardKey(key: Int, pressed: Boolean) {
    keyboardState[key] = pressed
}
override fun setControllerPortDevice(port: Int, device: Int) {
    lastPortDevice = port to device
}
```

- [ ] **Step 3: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt \
       player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/TestFakes.kt
git commit -m "feat: add mouse, keyboard, and port device methods to LibretroController"
```

---

### Task 5: State, Intent, and ViewModel for control tab selection

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/state/EmulationState.kt`
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/intent/EmulationIntent.kt`
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt`

- [ ] **Step 1: Add ControlTab enum and state field to EmulationState**

Add the enum before the EmulationState class:

```kotlin
/** Input mode tabs available on the secondary screen controls page. */
enum class ControlTab(val id: String) {
    GAMEPAD("gamepad"),
    KEYBOARD("keyboard"),
    TRACKPAD("trackpad");

    companion object {
        fun fromId(id: String): ControlTab = entries.find { it.id == id } ?: GAMEPAD
    }
}
```

Add field to EmulationState (after `touchControlPort`):

```kotlin
/** Which input tab is active on the secondary screen controls page. */
val selectedControlTab: ControlTab = ControlTab.GAMEPAD,
```

- [ ] **Step 2: Add SelectControlTab intent**

Add to EmulationIntent:

```kotlin
// Secondary screen control tab
data class SelectControlTab(val tab: ControlTab) : EmulationIntent
```

- [ ] **Step 3: Handle intent in EmulationViewModel**

In the `when (intent)` block, add:

```kotlin
is EmulationIntent.SelectControlTab -> {
    _state.update { it.copy(selectedControlTab = intent.tab) }
    // Persist to DeviceSettingEntity
    val consoleId = _state.value.consoleId
    if (consoleId.isNotEmpty()) {
        database.spelaDatabaseQueries.insertDeviceSetting(
            "control_tab:$consoleId", intent.tab.id
        )
    }
}
```

In `startGame()`, after setting `touchControlPort = 0`, load the persisted tab:

```kotlin
val savedTab = database.spelaDatabaseQueries.getDeviceSetting("control_tab:$consoleId")
    .executeAsOneOrNull()
    ?.let { ControlTab.fromId(it) }
    ?: ControlTab.GAMEPAD
```

Then include in the state update: `selectedControlTab = savedTab`

- [ ] **Step 4: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/state/EmulationState.kt \
       player/shared/src/commonMain/kotlin/com/spela/player/presentation/intent/EmulationIntent.kt \
       player/shared/src/commonMain/kotlin/com/spela/player/presentation/viewmodel/EmulationViewModel.kt
git commit -m "feat: add control tab state, intent, and persistence via DeviceSettingEntity"
```

---

### Task 6: RETROK key mapping constants in Kotlin

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/RetroKey.kt`

- [ ] **Step 1: Create RetroKey constants object**

```kotlin
package com.spela.player.presentation.ui.feature.ingame

/**
 * Libretro keyboard key codes (RETROK_*).
 * Values must match the retro_key enum in libretro.h.
 */
object RetroKey {
    const val BACKSPACE   = 8
    const val TAB         = 9
    const val RETURN      = 13
    const val ESCAPE      = 27
    const val SPACE       = 32
    const val EXCLAIM     = 33
    const val QUOTEDBL    = 34
    const val HASH        = 35
    const val DOLLAR      = 36
    const val AMPERSAND   = 38
    const val QUOTE       = 39
    const val LEFTPAREN   = 40
    const val RIGHTPAREN  = 41
    const val ASTERISK    = 42
    const val PLUS        = 43
    const val COMMA       = 44
    const val MINUS       = 45
    const val PERIOD      = 46
    const val SLASH       = 47
    const val KEY_0       = 48
    const val KEY_1       = 49
    const val KEY_2       = 50
    const val KEY_3       = 51
    const val KEY_4       = 52
    const val KEY_5       = 53
    const val KEY_6       = 54
    const val KEY_7       = 55
    const val KEY_8       = 56
    const val KEY_9       = 57
    const val COLON       = 58
    const val SEMICOLON   = 59
    const val LESS        = 60
    const val EQUALS      = 61
    const val GREATER     = 62
    const val QUESTION    = 63
    const val AT          = 64
    const val LEFTBRACKET  = 91
    const val BACKSLASH    = 92
    const val RIGHTBRACKET = 93
    const val CARET        = 94
    const val UNDERSCORE   = 95
    const val BACKQUOTE    = 96
    const val A = 97; const val B = 98; const val C = 99; const val D = 100
    const val E = 101; const val F = 102; const val G = 103; const val H = 104
    const val I = 105; const val J = 106; const val K = 107; const val L = 108
    const val M = 109; const val N = 110; const val O = 111; const val P = 112
    const val Q = 113; const val R = 114; const val S = 115; const val T = 116
    const val U = 117; const val V = 118; const val W = 119; const val X = 120
    const val Y = 121; const val Z = 122
    const val DELETE      = 127
    const val UP          = 273
    const val DOWN        = 274
    const val RIGHT       = 275
    const val LEFT        = 276
    const val INSERT      = 277
    const val HOME        = 278
    const val END         = 279
    const val PAGEUP      = 280
    const val PAGEDOWN    = 281
    const val F1  = 282; const val F2  = 283; const val F3  = 284; const val F4  = 285
    const val F5  = 286; const val F6  = 287; const val F7  = 288; const val F8  = 289
    const val F9  = 290; const val F10 = 291; const val F11 = 292; const val F12 = 293
    const val NUMLOCK     = 300
    const val CAPSLOCK    = 301
    const val SCROLLOCK   = 302
    const val RSHIFT      = 303
    const val LSHIFT      = 304
    const val RCTRL       = 305
    const val LCTRL       = 306
    const val RALT        = 307
    const val LALT        = 308
    const val LSUPER      = 311
    const val RSUPER      = 312
    const val HELP        = 315
    const val BREAK       = 318
}
```

- [ ] **Step 2: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/RetroKey.kt
git commit -m "feat: add RetroKey constants matching libretro RETROK enum"
```

---

### Task 7: Refactor SecondaryControlsPage with segmented control tabs

**Files:**
- Modify: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/SecondaryControlsPage.kt`

- [ ] **Step 1: Add ControlTab import and segmented control**

Rewrite `SecondaryControlsPage` to show a segmented control at the top, with the gamepad content in the Gamepad tab and placeholders for Keyboard/Trackpad:

```kotlin
package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.state.ControlTab
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.LibretroController

/**
 * Controls page for the secondary screen companion.
 *
 * Shows a segmented control (Gamepad | Keyboard | Trackpad) above the
 * active input mode content. The Gamepad tab includes the P1/P2 port
 * selector and platform touch controls.
 */
@Composable
fun SecondaryControlsPage(
    controller: LibretroController,
    touchControlPort: Int,
    selectedTab: ControlTab,
    consoleId: String,
    onSelectPort: (Int) -> Unit,
    onSelectTab: (ControlTab) -> Unit,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    onMouseMove: (dx: Float, dy: Float) -> Unit,
    onMouseButton: (left: Boolean, right: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Segmented control tab selector
        ControlTabSelector(
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
        )

        // Tab content filling remaining space
        when (selectedTab) {
            ControlTab.GAMEPAD -> GamepadTabContent(
                controller = controller,
                touchControlPort = touchControlPort,
                onSelectPort = onSelectPort,
                modifier = Modifier.weight(1f),
            )
            ControlTab.KEYBOARD -> SecondaryKeyboardTab(
                consoleId = consoleId,
                onKeyDown = onKeyDown,
                onKeyUp = onKeyUp,
                modifier = Modifier.weight(1f),
            )
            ControlTab.TRACKPAD -> SecondaryTrackpadTab(
                onMouseMove = onMouseMove,
                onMouseButton = onMouseButton,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Gamepad tab content: P1/P2 port selector + platform touch controls.
 */
@Composable
private fun GamepadTabContent(
    controller: LibretroController,
    touchControlPort: Int,
    onSelectPort: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PortSelectorRow(
            selectedPort = touchControlPort,
            onSelectPort = onSelectPort,
        )
        PlatformTouchControls(
            controller = controller,
            modifier = Modifier.weight(1f),
            port = touchControlPort,
        )
    }
}

/**
 * Pill-style segmented control for switching between input tabs.
 */
@Composable
private fun ControlTabSelector(
    selectedTab: ControlTab,
    onSelectTab: (ControlTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "Input mode: ${selectedTab.id}"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlTab.entries.forEachIndexed { index, tab ->
            if (index > 0) Spacer(Modifier.width(4.dp))
            TabPill(
                label = tab.id.replaceFirstChar { it.uppercase() },
                isSelected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                contentDesc = "${tab.id.replaceFirstChar { it.uppercase() }} input mode",
            )
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentDesc: String,
) {
    val backgroundColor = if (isSelected) SpColor.Primary else SpColor.SurfaceVariant
    val textColor = if (isSelected) SpColor.OnBackground else SpColor.OnBackgroundSecondary

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(SpSpacing.Small))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.XSmall)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SpTypography.LabelMedium,
            color = textColor,
        )
    }
}

// --- Port selector (unchanged from original) ---

@Composable
private fun PortSelectorRow(
    selectedPort: Int,
    onSelectPort: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(vertical = SpSpacing.Small)
            .semantics {
                contentDescription = "Control port: Player ${selectedPort + 1}"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PortPillButton(label = "P1", port = 0, isSelected = selectedPort == 0, onSelect = onSelectPort)
        Spacer(Modifier.width(SpSpacing.Small))
        PortPillButton(label = "P2", port = 1, isSelected = selectedPort == 1, onSelect = onSelectPort)
    }
}

@Composable
private fun PortPillButton(
    label: String,
    port: Int,
    isSelected: Boolean,
    onSelect: (Int) -> Unit,
) {
    val backgroundColor = if (isSelected) SpColor.Primary else SpColor.SurfaceVariant
    val textColor = if (isSelected) SpColor.OnBackground else SpColor.OnBackgroundSecondary

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(SpSpacing.Small))
            .background(backgroundColor)
            .clickable { onSelect(port) }
            .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
            .semantics { contentDescription = "Player ${port + 1} controls" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = SpTypography.LabelMedium, color = textColor)
    }
}
```

- [ ] **Step 2: Update SecondaryScreenContent.kt to pass new params**

In `SecondaryScreenContent.kt`, update the PAGE_CONTROLS case to pass the new parameters:

```kotlin
PAGE_CONTROLS -> {
    SecondaryControlsPage(
        controller = controller,
        touchControlPort = state.touchControlPort,
        selectedTab = state.selectedControlTab,
        consoleId = state.consoleId,
        onSelectPort = { port ->
            viewModel.onIntent(EmulationIntent.SelectTouchControlPort(port))
        },
        onSelectTab = { tab ->
            viewModel.onIntent(EmulationIntent.SelectControlTab(tab))
        },
        onKeyDown = { key ->
            controller.setKeyboardKey(key, true)
        },
        onKeyUp = { key ->
            controller.setKeyboardKey(key, false)
        },
        onMouseMove = { dx, dy ->
            controller.setMouse(0, dx.toInt().toShort(), dy.toInt().toShort(), false, false)
        },
        onMouseButton = { left, right ->
            controller.setMouse(0, 0, 0, left, right)
        },
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/SecondaryControlsPage.kt \
       player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/SecondaryScreenContent.kt
git commit -m "feat: refactor controls page with segmented tab selector"
```

---

### Task 8: Keyboard tab composable

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/SecondaryKeyboardTab.kt`

- [ ] **Step 1: Create the keyboard tab with layered layout**

```kotlin
package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Keyboard layer identifiers.
 */
private enum class KeyboardLayer {
    QWERTY, FN, SYMBOLS, PLATFORM,
}

/**
 * A single key definition for the on-screen keyboard.
 */
private data class KeyDef(
    val label: String,
    val retroKey: Int,
    val widthWeight: Float = 1f,
)

/**
 * Virtual keyboard tab for the secondary screen controls page.
 * Uses a layer system: QWERTY (default), Fn, Symbols, plus
 * optional per-platform layers for special keys.
 */
@Composable
fun SecondaryKeyboardTab(
    consoleId: String,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeLayer by remember { mutableStateOf(KeyboardLayer.QWERTY) }
    var shiftActive by remember { mutableStateOf(false) }
    var capsLock by remember { mutableStateOf(false) }
    val effectiveShift = shiftActive || capsLock

    // Modifier keys (sticky behavior)
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }

    val platformLayer = getPlatformLayerKeys(consoleId)
    val hasPlatformLayer = platformLayer != null

    val onKey: (Int, Boolean) -> Unit = { key, pressed ->
        if (pressed) onKeyDown(key) else onKeyUp(key)
        // Auto-release sticky modifiers after a non-modifier key press
        if (!pressed && key != RetroKey.LSHIFT && key != RetroKey.LCTRL && key != RetroKey.LALT) {
            if (shiftActive && !capsLock) {
                shiftActive = false
                onKeyUp(RetroKey.LSHIFT)
            }
            if (ctrlActive) {
                ctrlActive = false
                onKeyUp(RetroKey.LCTRL)
            }
            if (altActive) {
                altActive = false
                onKeyUp(RetroKey.LALT)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .semantics { contentDescription = "Virtual keyboard, ${activeLayer.name} layer" },
        verticalArrangement = Arrangement.Bottom,
    ) {
        // Key rows for the active layer
        val rows = when (activeLayer) {
            KeyboardLayer.QWERTY -> getQwertyRows(effectiveShift)
            KeyboardLayer.FN -> getFnRows()
            KeyboardLayer.SYMBOLS -> getSymbolRows()
            KeyboardLayer.PLATFORM -> platformLayer ?: getQwertyRows(effectiveShift)
        }

        rows.forEach { row ->
            KeyRow(keys = row, onKey = onKey)
        }

        // Bottom control row: layer switches + space + enter + arrows
        BottomControlRow(
            activeLayer = activeLayer,
            shiftActive = effectiveShift,
            ctrlActive = ctrlActive,
            altActive = altActive,
            hasPlatformLayer = hasPlatformLayer,
            platformName = getPlatformLayerName(consoleId),
            onLayerSwitch = { layer ->
                activeLayer = if (activeLayer == layer) KeyboardLayer.QWERTY else layer
            },
            onShiftToggle = {
                shiftActive = !shiftActive
                if (shiftActive) onKeyDown(RetroKey.LSHIFT) else onKeyUp(RetroKey.LSHIFT)
            },
            onShiftDoubleTap = {
                capsLock = !capsLock
                shiftActive = false
                if (capsLock) onKeyDown(RetroKey.LSHIFT) else onKeyUp(RetroKey.LSHIFT)
            },
            onCtrlToggle = {
                ctrlActive = !ctrlActive
                if (ctrlActive) onKeyDown(RetroKey.LCTRL) else onKeyUp(RetroKey.LCTRL)
            },
            onAltToggle = {
                altActive = !altActive
                if (altActive) onKeyDown(RetroKey.LALT) else onKeyUp(RetroKey.LALT)
            },
            onKey = onKey,
        )
    }
}

@Composable
private fun KeyRow(
    keys: List<KeyDef>,
    onKey: (Int, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        keys.forEach { keyDef ->
            KeyButton(
                keyDef = keyDef,
                onKey = onKey,
                modifier = Modifier.weight(keyDef.widthWeight),
            )
        }
    }
}

@Composable
private fun KeyButton(
    keyDef: KeyDef,
    onKey: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onKey(keyDef.retroKey, isPressed)
    }

    val bgColor = if (isPressed) SpColor.Primary else SpColor.SurfaceVariant

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(interactionSource = interactionSource, indication = null) { }
            .semantics { contentDescription = "Key ${keyDef.label}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = keyDef.label,
            fontSize = 11.sp,
            color = SpColor.OnBackground,
        )
    }
}

@Composable
private fun BottomControlRow(
    activeLayer: KeyboardLayer,
    shiftActive: Boolean,
    ctrlActive: Boolean,
    altActive: Boolean,
    hasPlatformLayer: Boolean,
    platformName: String?,
    onLayerSwitch: (KeyboardLayer) -> Unit,
    onShiftToggle: () -> Unit,
    onShiftDoubleTap: () -> Unit,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onKey: (Int, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Shift
        ModifierPill(label = "Shift", isActive = shiftActive, onClick = onShiftToggle, modifier = Modifier.weight(1.2f))
        // Ctrl
        ModifierPill(label = "Ctrl", isActive = ctrlActive, onClick = onCtrlToggle, modifier = Modifier.weight(1f))
        // Alt
        ModifierPill(label = "Alt", isActive = altActive, onClick = onAltToggle, modifier = Modifier.weight(1f))
        // Fn
        LayerPill(label = "Fn", isActive = activeLayer == KeyboardLayer.FN, onClick = { onLayerSwitch(KeyboardLayer.FN) }, modifier = Modifier.weight(0.8f))
        // Sym
        LayerPill(label = "Sym", isActive = activeLayer == KeyboardLayer.SYMBOLS, onClick = { onLayerSwitch(KeyboardLayer.SYMBOLS) }, modifier = Modifier.weight(0.8f))
        // Platform layer (if available)
        if (hasPlatformLayer && platformName != null) {
            LayerPill(label = platformName, isActive = activeLayer == KeyboardLayer.PLATFORM, onClick = { onLayerSwitch(KeyboardLayer.PLATFORM) }, modifier = Modifier.weight(1f))
        }
        // Space
        KeyButton(keyDef = KeyDef("Space", RetroKey.SPACE, 2f), onKey = onKey, modifier = Modifier.weight(2f))
        // Enter
        KeyButton(keyDef = KeyDef("Ent", RetroKey.RETURN, 1f), onKey = onKey, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ModifierPill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isActive) SpColor.Primary else SpColor.SurfaceVariant
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label ${if (isActive) "active" else "inactive"}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 10.sp, color = SpColor.OnBackground)
    }
}

@Composable
private fun LayerPill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isActive) SpColor.Primary.copy(alpha = 0.7f) else SpColor.SurfaceVariant.copy(alpha = 0.6f)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label layer ${if (isActive) "active" else "inactive"}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 10.sp, color = SpColor.OnBackground)
    }
}

// --- Key layout definitions ---

private fun getQwertyRows(shift: Boolean): List<List<KeyDef>> {
    val nums = listOf(
        KeyDef("1", RetroKey.KEY_1), KeyDef("2", RetroKey.KEY_2), KeyDef("3", RetroKey.KEY_3),
        KeyDef("4", RetroKey.KEY_4), KeyDef("5", RetroKey.KEY_5), KeyDef("6", RetroKey.KEY_6),
        KeyDef("7", RetroKey.KEY_7), KeyDef("8", RetroKey.KEY_8), KeyDef("9", RetroKey.KEY_9),
        KeyDef("0", RetroKey.KEY_0),
    )
    val topRow = listOf("QWERTYUIOP").first().map { c ->
        val label = if (shift) c.uppercase() else c.lowercase()
        KeyDef(label, RetroKey.A + (c.lowercaseChar() - 'a'))
    }
    val middleRow = "ASDFGHJKL".map { c ->
        val label = if (shift) c.uppercase() else c.lowercase()
        KeyDef(label, RetroKey.A + (c.lowercaseChar() - 'a'))
    }
    val bottomRow = buildList {
        "ZXCVBNM".forEach { c ->
            val label = if (shift) c.uppercase() else c.lowercase()
            add(KeyDef(label, RetroKey.A + (c.lowercaseChar() - 'a')))
        }
        add(KeyDef("⌫", RetroKey.BACKSPACE, 1.5f))
    }
    return listOf(nums, topRow, middleRow, bottomRow)
}

private fun getFnRows(): List<List<KeyDef>> = listOf(
    listOf(
        KeyDef("F1", RetroKey.F1), KeyDef("F2", RetroKey.F2), KeyDef("F3", RetroKey.F3),
        KeyDef("F4", RetroKey.F4), KeyDef("F5", RetroKey.F5), KeyDef("F6", RetroKey.F6),
    ),
    listOf(
        KeyDef("F7", RetroKey.F7), KeyDef("F8", RetroKey.F8), KeyDef("F9", RetroKey.F9),
        KeyDef("F10", RetroKey.F10), KeyDef("F11", RetroKey.F11), KeyDef("F12", RetroKey.F12),
    ),
    listOf(
        KeyDef("Esc", RetroKey.ESCAPE), KeyDef("Tab", RetroKey.TAB),
        KeyDef("Ins", RetroKey.INSERT), KeyDef("Del", RetroKey.DELETE),
    ),
    listOf(
        KeyDef("Home", RetroKey.HOME), KeyDef("End", RetroKey.END),
        KeyDef("PgUp", RetroKey.PAGEUP), KeyDef("PgDn", RetroKey.PAGEDOWN),
    ),
    listOf(
        KeyDef("←", RetroKey.LEFT), KeyDef("↑", RetroKey.UP),
        KeyDef("↓", RetroKey.DOWN), KeyDef("→", RetroKey.RIGHT),
    ),
)

private fun getSymbolRows(): List<List<KeyDef>> = listOf(
    listOf(
        KeyDef("!", RetroKey.EXCLAIM), KeyDef("@", RetroKey.AT), KeyDef("#", RetroKey.HASH),
        KeyDef("$", RetroKey.DOLLAR), KeyDef("%", RetroKey.KEY_5), // shift+5
        KeyDef("^", RetroKey.CARET), KeyDef("&", RetroKey.AMPERSAND),
        KeyDef("*", RetroKey.ASTERISK), KeyDef("(", RetroKey.LEFTPAREN),
        KeyDef(")", RetroKey.RIGHTPAREN),
    ),
    listOf(
        KeyDef("-", RetroKey.MINUS), KeyDef("=", RetroKey.EQUALS),
        KeyDef("[", RetroKey.LEFTBRACKET), KeyDef("]", RetroKey.RIGHTBRACKET),
        KeyDef("\\", RetroKey.BACKSLASH), KeyDef("|", RetroKey.KEY_0), // approximation
        KeyDef(";", RetroKey.SEMICOLON), KeyDef("'", RetroKey.QUOTE),
    ),
    listOf(
        KeyDef("`", RetroKey.BACKQUOTE), KeyDef("~", RetroKey.TILDE),
        KeyDef("<", RetroKey.LESS), KeyDef(">", RetroKey.GREATER),
        KeyDef("/", RetroKey.SLASH), KeyDef("?", RetroKey.QUESTION),
        KeyDef(",", RetroKey.COMMA), KeyDef(".", RetroKey.PERIOD),
    ),
    listOf(
        KeyDef("+", RetroKey.PLUS), KeyDef("_", RetroKey.UNDERSCORE),
        KeyDef(":", RetroKey.COLON), KeyDef("\"", RetroKey.QUOTEDBL),
        KeyDef("⌫", RetroKey.BACKSPACE, 1.5f),
    ),
)

// --- Per-platform layers ---

private fun getPlatformLayerName(consoleId: String): String? = when (consoleId.lowercase()) {
    "amiga", "ademo" -> "Amiga"
    "dos", "ddemo" -> "DOS"
    "c64" -> "C64"
    "c128" -> "C128"
    "vic20" -> "VIC"
    "msx", "msx2" -> "MSX"
    else -> null
}

private fun getPlatformLayerKeys(consoleId: String): List<List<KeyDef>>? = when (consoleId.lowercase()) {
    "amiga", "ademo" -> listOf(
        listOf(KeyDef("L Amiga", RetroKey.LSUPER), KeyDef("R Amiga", RetroKey.RSUPER), KeyDef("Help", RetroKey.HELP)),
        listOf(
            KeyDef("F1", RetroKey.F1), KeyDef("F2", RetroKey.F2), KeyDef("F3", RetroKey.F3),
            KeyDef("F4", RetroKey.F4), KeyDef("F5", RetroKey.F5),
        ),
        listOf(
            KeyDef("F6", RetroKey.F6), KeyDef("F7", RetroKey.F7), KeyDef("F8", RetroKey.F8),
            KeyDef("F9", RetroKey.F9), KeyDef("F10", RetroKey.F10),
        ),
    )
    "dos", "ddemo" -> listOf(
        listOf(KeyDef("Esc", RetroKey.ESCAPE), KeyDef("Tab", RetroKey.TAB), KeyDef("Del", RetroKey.DELETE)),
        listOf(
            KeyDef("F1", RetroKey.F1), KeyDef("F2", RetroKey.F2), KeyDef("F3", RetroKey.F3),
            KeyDef("F4", RetroKey.F4), KeyDef("F5", RetroKey.F5), KeyDef("F6", RetroKey.F6),
        ),
        listOf(
            KeyDef("←", RetroKey.LEFT), KeyDef("↑", RetroKey.UP),
            KeyDef("↓", RetroKey.DOWN), KeyDef("→", RetroKey.RIGHT),
        ),
    )
    "c64" -> listOf(
        listOf(KeyDef("RUN/STOP", RetroKey.ESCAPE), KeyDef("RESTORE", RetroKey.PAGEUP)),
        listOf(KeyDef("C=", RetroKey.LCTRL), KeyDef("CTRL", RetroKey.TAB)),
        listOf(
            KeyDef("←", RetroKey.LEFT), KeyDef("↑", RetroKey.UP),
            KeyDef("↓", RetroKey.DOWN), KeyDef("→", RetroKey.RIGHT),
        ),
    )
    "c128" -> listOf(
        listOf(KeyDef("RUN/STOP", RetroKey.ESCAPE), KeyDef("RESTORE", RetroKey.PAGEUP)),
        listOf(KeyDef("C=", RetroKey.LCTRL), KeyDef("CTRL", RetroKey.TAB), KeyDef("40/80", RetroKey.F12)),
        listOf(
            KeyDef("←", RetroKey.LEFT), KeyDef("↑", RetroKey.UP),
            KeyDef("↓", RetroKey.DOWN), KeyDef("→", RetroKey.RIGHT),
        ),
    )
    "vic20" -> listOf(
        listOf(KeyDef("RUN/STOP", RetroKey.ESCAPE), KeyDef("RESTORE", RetroKey.PAGEUP)),
        listOf(KeyDef("C=", RetroKey.LCTRL)),
    )
    "msx", "msx2" -> listOf(
        listOf(KeyDef("SELECT", RetroKey.F1), KeyDef("STOP", RetroKey.F2)),
        listOf(KeyDef("GRAPH", RetroKey.F3), KeyDef("CODE", RetroKey.F4)),
        listOf(
            KeyDef("←", RetroKey.LEFT), KeyDef("↑", RetroKey.UP),
            KeyDef("↓", RetroKey.DOWN), KeyDef("→", RetroKey.RIGHT),
        ),
    )
    else -> null
}
```

- [ ] **Step 2: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/SecondaryKeyboardTab.kt
git commit -m "feat: add virtual keyboard tab with layered layout and platform-specific keys"
```

---

### Task 9: Trackpad tab composable

**Files:**
- Create: `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/SecondaryTrackpadTab.kt`

- [ ] **Step 1: Create the trackpad tab**

```kotlin
package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TRACKPAD_SENSITIVITY = 1.5f
private const val TAP_TIMEOUT_MS = 200L
private const val TAP_MOVEMENT_THRESHOLD = 10f

/**
 * Trackpad tab for the secondary screen controls page.
 *
 * Provides relative-mode mouse input: dragging moves the cursor
 * relative to the current position. Includes dedicated left/right
 * click buttons and tap gesture shortcuts.
 */
@Composable
fun SecondaryTrackpadTab(
    onMouseMove: (dx: Float, dy: Float) -> Unit,
    onMouseButton: (left: Boolean, right: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.Small),
    ) {
        // Trackpad area
        TrackpadSurface(
            onMouseMove = onMouseMove,
            onTap = {
                // Single-finger tap = left click
                onMouseButton(true, false)
                onMouseButton(false, false)
            },
            onTwoFingerTap = {
                // Two-finger tap = right click
                onMouseButton(false, true)
                onMouseButton(false, false)
            },
            isButtonHeld = leftPressed,
            onDragWithButton = { dx, dy ->
                // Click-and-drag while button held
                onMouseMove(dx, dy)
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Spacer(Modifier.height(SpSpacing.Small))

        // Click buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            ClickButton(
                label = "Left Click",
                isPressed = leftPressed,
                onPressChange = { pressed ->
                    leftPressed = pressed
                    onMouseButton(pressed, rightPressed)
                },
                modifier = Modifier.weight(1f),
            )
            ClickButton(
                label = "Right Click",
                isPressed = rightPressed,
                onPressChange = { pressed ->
                    rightPressed = pressed
                    onMouseButton(leftPressed, pressed)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrackpadSurface(
    onMouseMove: (dx: Float, dy: Float) -> Unit,
    onTap: () -> Unit,
    onTwoFingerTap: () -> Unit,
    isButtonHeld: Boolean,
    onDragWithButton: (dx: Float, dy: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SpColor.SurfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, SpColor.OnBackgroundTertiary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .pointerInput(isButtonHeld) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    firstDown.consume()

                    var totalDx = 0f
                    var totalDy = 0f
                    var pointerCount = 1
                    val startTime = firstDown.uptimeMillis
                    var prevPosition = firstDown.position

                    // Track movement
                    while (true) {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }

                        if (activePointers.isEmpty()) {
                            // All fingers lifted
                            val elapsed = (event.changes.firstOrNull()?.uptimeMillis ?: startTime) - startTime
                            val wasTap = elapsed < TAP_TIMEOUT_MS &&
                                kotlin.math.abs(totalDx) < TAP_MOVEMENT_THRESHOLD &&
                                kotlin.math.abs(totalDy) < TAP_MOVEMENT_THRESHOLD

                            if (wasTap) {
                                if (pointerCount >= 2) onTwoFingerTap() else onTap()
                            }
                            break
                        }

                        pointerCount = maxOf(pointerCount, activePointers.size)

                        // Only move cursor with single finger
                        if (activePointers.size == 1) {
                            val current = activePointers.first()
                            val dx = (current.position.x - prevPosition.x) * TRACKPAD_SENSITIVITY
                            val dy = (current.position.y - prevPosition.y) * TRACKPAD_SENSITIVITY
                            totalDx += dx
                            totalDy += dy
                            prevPosition = current.position

                            if (kotlin.math.abs(dx) > 0.5f || kotlin.math.abs(dy) > 0.5f) {
                                if (isButtonHeld) {
                                    onDragWithButton(dx, dy)
                                } else {
                                    onMouseMove(dx, dy)
                                }
                            }
                            current.consume()
                        } else {
                            // Multi-finger: update position for next delta calculation
                            prevPosition = activePointers.first().position
                        }
                    }
                }
            }
            .semantics { contentDescription = "Trackpad area, drag to move cursor" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "🖱",
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackgroundTertiary.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun ClickButton(
    label: String,
    isPressed: Boolean,
    onPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isPressed) SpColor.Primary else SpColor.SurfaceVariant

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onPressChange(true)
                    // Wait for up
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) {
                            onPressChange(false)
                            break
                        }
                    }
                }
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = SpTypography.LabelMedium,
            color = SpColor.OnBackground,
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/feature/ingame/SecondaryTrackpadTab.kt
git commit -m "feat: add trackpad tab with relative pointer and click buttons"
```

---

### Task 10: Desktop E2E tests

**Files:**
- Modify: `player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/SecondaryScreenControlsTest.kt`

- [ ] **Step 1: Update existing tests for new parameters and add tab tests**

Update existing tests to pass the new required parameters, and add tests for tab switching, keyboard, and trackpad:

```kotlin
package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.state.ControlTab
import com.spela.player.presentation.ui.feature.ingame.SecondaryControlsPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SecondaryScreenControlsTest {

    // --- Gamepad tab tests (updated from originals) ---

    @Test
    fun controlsPageShowsTabSelector() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        // Tab selector should be visible
        onNodeWithContentDescription("Gamepad input mode").assertExists()
        onNodeWithContentDescription("Keyboard input mode").assertExists()
        onNodeWithContentDescription("Trackpad input mode").assertExists()
        onNodeWithContentDescription("Input mode: gamepad").assertExists()
    }

    @Test
    fun gamepadTabShowsPortSelector() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Player 1 controls").assertExists()
        onNodeWithContentDescription("Player 2 controls").assertExists()
    }

    @Test
    fun tabSwitchCallsCallback() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        var selectedTab: ControlTab? = null

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = { selectedTab = it },
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Keyboard input mode").performClick()
        waitForIdle()

        assertEquals(ControlTab.KEYBOARD, selectedTab)
    }

    // --- Keyboard tab tests ---

    @Test
    fun keyboardTabShowsQwertyLayer() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.KEYBOARD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Virtual keyboard, QWERTY layer").assertExists()
        onNodeWithContentDescription("Key q").assertExists()
        onNodeWithContentDescription("Key a").assertExists()
        onNodeWithContentDescription("Key z").assertExists()
    }

    @Test
    fun keyboardTabShowsPlatformLayerForAmiga() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.KEYBOARD,
                consoleId = "amiga",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        // Amiga layer button should exist
        onNodeWithContentDescription("Amiga layer inactive").assertExists()
    }

    @Test
    fun keyboardTabNoPlatformLayerForSnes() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.KEYBOARD,
                consoleId = "snes",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        // No platform-specific layer for SNES
        onAllNodesWithContentDescription("layer inactive", substring = true)
            .filter(hasContentDescription("Amiga", substring = true).not())
    }

    // --- Trackpad tab tests ---

    @Test
    fun trackpadTabShowsClickButtons() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.TRACKPAD,
                consoleId = "dos",
                onSelectPort = {},
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Trackpad area, drag to move cursor").assertExists()
        onNodeWithContentDescription("Left Click").assertExists()
        onNodeWithContentDescription("Right Click").assertExists()
    }

    // --- Port selector callback (updated) ---

    @Test
    fun portSelectorCallsCallback() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        var selectedPort = -1

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 0,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = { selectedPort = it },
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Player 2 controls").performClick()
        waitForIdle()

        assertEquals(1, selectedPort)
    }

    // -- Helpers ---------------------------------------------------------------

    private fun createHarnessWithNesGame(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        return harness
    }

    // -- Integration tests via full harness ------------------------------------

    @Test
    fun touchControlPortResetsOnNewGame() = runComposeUiTest {
        val harness = createHarnessWithNesGame()

        harness.emulationViewModel.onIntent(EmulationIntent.StartGame(gameId = "1"))
        mainClock.autoAdvance = false
        repeat(4) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        harness.emulationViewModel.onIntent(EmulationIntent.SelectTouchControlPort(1))
        mainClock.autoAdvance = false
        repeat(2) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        assertEquals(1, harness.emulationViewModel.state.value.touchControlPort)

        harness.emulationViewModel.onIntent(EmulationIntent.StopGame)
        mainClock.autoAdvance = false
        repeat(4) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
        }
        mainClock.autoAdvance = true

        assertEquals(0, harness.emulationViewModel.state.value.touchControlPort)
    }

    @Test
    fun portSelectorCallsCallbackForP1() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        var selectedPort = -1

        setContent {
            SecondaryControlsPage(
                controller = harness.libretroController,
                touchControlPort = 1,
                selectedTab = ControlTab.GAMEPAD,
                consoleId = "snes",
                onSelectPort = { selectedPort = it },
                onSelectTab = {},
                onKeyDown = {},
                onKeyUp = {},
                onMouseMove = { _, _ -> },
                onMouseButton = { _, _ -> },
            )
        }
        waitForIdle()

        onNodeWithContentDescription("Player 1 controls").performClick()
        waitForIdle()

        assertEquals(0, selectedPort)
    }
}
```

- [ ] **Step 2: Run tests**

Run: `cd player && ./run-desktop-tests.sh`
Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add player/desktop/src/desktopTest/kotlin/com/spela/player/desktop/e2e/SecondaryScreenControlsTest.kt
git commit -m "test: update and add desktop E2E tests for control tab switching"
```

---

### Task 11: Wire LibretroController implementations

**Files:**
- Find and modify the concrete `LibretroController` implementations (both Android and Desktop) to call the new JNI methods.

- [ ] **Step 1: Find and update the real LibretroController implementations**

Add implementations of `setMouse`, `setKeyboardKey`, and `setControllerPortDevice` that delegate to the JNI layer:

```kotlin
override fun setMouse(port: Int, dx: Short, dy: Short, left: Boolean, right: Boolean) {
    LibretroJni.nativeSetInputMouse(port, dx, dy, left, right)
}

override fun setKeyboardKey(key: Int, pressed: Boolean) {
    LibretroJni.nativeSetInputKeyboard(key, pressed)
}

override fun setControllerPortDevice(port: Int, device: Int) {
    LibretroJni.nativeSetControllerPortDevice(port, device)
}
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: wire LibretroController implementations to JNI for mouse and keyboard"
```

---

### Task 12: Update ExpectedSchema for DeviceSettingEntity usage

**Files:**
- Verify: `player/shared/src/commonMain/kotlin/com/spela/player/data/local/ExpectedSchema.kt`

- [ ] **Step 1: Verify DeviceSettingEntity is in expected schema**

The `DeviceSettingEntity` is already in the schema list. No new table is needed since we're using the existing key-value store with keys like `"control_tab:snes"`. Verify and move on.

- [ ] **Step 2: Commit (only if changes needed)**

No commit expected — this is a verification step.
