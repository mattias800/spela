# Building a borderless / transparent–title-bar desktop app (Compose Multiplatform)

A field guide for creating cross-platform desktop apps that have **no visible app
bar** — the app's own content (gradient, header, controls) extends edge-to-edge to
the top of the window and shows *through* a transparent native title bar, like Spela.

This is deceptively tricky. The OS still owns the window frame; you're making it
transparent and drawing under it, then patching back the behaviours you lost
(dragging, content not colliding with the native caption buttons). The mechanism is
**different on each platform**, and the Windows path has two non-obvious traps that
cost real debugging time. Read this before reaching for `undecorated = true`.

> **Don't use `undecorated = true`** unless you intend to re-implement the entire
> window frame yourself (drag, resize from all 8 edges, snap, maximize, native
> min/max/close buttons, per-OS conventions). Keeping the native frame and just
> making the title bar transparent is far less work and feels native.

---

## The model

| Platform | Mechanism | Caption buttons | Draggable by default? |
|---|---|---|---|
| **macOS** | `apple.awt.*` rootPane client properties (OpenJDK) | top-**left** (traffic lights) | **Yes** |
| **Windows** | JetBrains Runtime (JBR) `WindowDecorations.CustomTitleBar` | top-**right** | **No** — must opt in (see traps) |
| **Linux** | JBR `WindowDecorations.CustomTitleBar` | WM-dependent | Yes (WM handles it) |

Two consequences fall out of this table immediately:

1. **Caption buttons live in opposite corners** (macOS left, Windows right). Any
   interactive content you place in the *same* top corner as the buttons will
   collide with them. Inset that content (see "Content inset" below).
2. **Only Windows needs a manual drag fix.** Gate the Windows-specific code behind
   an `isWindows` check so you don't disturb the working macOS/Linux behaviour.

---

## macOS

Set three client properties on the window's `rootPane` (do it in a `LaunchedEffect`
inside the `Window {}` composable, where `window` is in scope):

```kotlin
window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)   // draw under the title bar
window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true) // make it transparent
window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false) // hide the title text
// Opaque fallback color shown for the split second before Compose first renders:
val bg = java.awt.Color(10, 10, 16)
window.background = bg; window.rootPane.background = bg; window.contentPane.background = bg
```

macOS handles dragging itself. Nothing else to do. Traffic lights stay top-left.

---

## Windows / Linux (JetBrains Runtime)

The transparent title bar comes from JBR's `WindowDecorations.CustomTitleBar`. Call
it **reflectively** so the app still launches on a non-JBR JDK (dev machines,
`./gradlew run` off a stock JDK) — `com.jetbrains.JBR` simply won't be present and
you degrade to a normal title bar instead of crashing.

```kotlin
val jbr = Class.forName("com.jetbrains.JBR")
val windowDecorations = jbr.getMethod("getWindowDecorations").invoke(null) ?: return // not JBR
val wdInterface  = Class.forName("com.jetbrains.WindowDecorations")
val ctbInterface = Class.forName("com.jetbrains.WindowDecorations\$CustomTitleBar")

val titleBar = wdInterface.getMethod("createCustomTitleBar").invoke(windowDecorations)
ctbInterface.getMethod("setHeight", java.lang.Float.TYPE).invoke(titleBar, 32f)

// setCustomTitleBar is overloaded ((Frame, ..) / (Dialog, ..)) and the param types
// vary by JBR version — match the overload to the actual window/title-bar types
// instead of hard-coding a signature (reflecting the impl class throws
// IllegalAccessException; use the PUBLIC interfaces).
val setMethod = wdInterface.methods.first { m ->
    m.name == "setCustomTitleBar" && m.parameterCount == 2 &&
        m.parameterTypes[0].isInstance(window) && m.parameterTypes[1].isInstance(titleBar)
}
setMethod.invoke(windowDecorations, window, titleBar)
```

**Packaging:** this only works when the app actually runs on JBR. Bundle JBR with
your distribution (`jpackage`/Conveyor/etc.). In CI, downloading the JBR runtime can
hit rate limits on the download host — cache it or pin a mirror.

**The native caption buttons are not app-resizable.** Their *height* tracks
`setHeight`; their *width* is owned by the OS. So "make the buttons narrower" isn't
available — change the height, or inset your content, instead.

### Trap 1 — the window won't drag (Windows)

Symptom: on Windows the window can't be moved by its top strip at all. Cause:
Compose renders into one big opaque heavyweight surface that covers the whole
window, so Windows hit-tests the entire title-bar strip as *client area* and never
starts a drag.

Fix: overlay an invisible strip across the top and, on each pointer event, tell JBR
the region is **caption** (non-client) via `forceHitTest(false)`. Windows then
handles a press there as a window drag — and the native min/max/close buttons keep
working, because they're caption too, so you can cover the full width.

```kotlin
// captured when you create the title bar:
val forceHitTest = ctbInterface.getMethod("forceHitTest", java.lang.Boolean.TYPE)
fun markDraggable() = forceHitTest.invoke(titleBar, false) // false = non-client = caption/draggable

// in the Window content, ON TOP of your app content, Windows only:
if (isWindows && titleBarActive) {
    Box(
        Modifier.align(Alignment.TopStart).fillMaxWidth().height(titleBarInset)
            .onPointerEvent(PointerEventType.Move)  { markDraggable() }
            .onPointerEvent(PointerEventType.Enter) { markDraggable() }
            .onPointerEvent(PointerEventType.Press) { markDraggable() }
    )
}
```

> **`forceHitTest` must be called synchronously during event dispatch.** Use
> `Modifier.onPointerEvent` (fires inside Compose's dispatch, within the AWT event),
> **not** `Modifier.pointerInput { awaitPointerEvent() }` — the coroutine resumes
> *after* the OS hit-test window has passed, so the call is ignored and dragging
> silently doesn't work. This is the single most time-wasting trap here.

`forceHitTest(true)` = client (your app handles it, no drag); `forceHitTest(false)`
= caption (OS handles drag / native buttons). For interactive content you place
*inside* the strip, call `forceHitTest(true)` over it so clicks reach your control.

### Trap 2 — the activation flag must be Compose state

JBR setup runs in a `LaunchedEffect` (it needs the AWT `window`), i.e. **after** the
first composition. If you store "is the custom title bar active?" in a plain
`var`, the composable that reads it (your drag strip, your inset calc) captures
`false` at first composition and **never recomposes** — so the drag strip is never
even added to the tree. Back it with `mutableStateOf` so flipping it triggers
recomposition:

```kotlin
private val titleBarActiveState = mutableStateOf(false)
private var titleBarActive: Boolean
    get() = titleBarActiveState.value
    set(v) { titleBarActiveState.value = v }
```

(We chased "drag does nothing" for a while before realizing the strip wasn't being
composed at all — no amount of `forceHitTest` tuning helps if the handler isn't in
the tree.)

---

## Content inset — keep interactive content off the native buttons

The whole point is full-bleed content, so your **background must reach the top
edge** (draw it with `Modifier.drawBehind`/`background` on a full-size root). But
**interactive** content at the top must not sit under the native caption buttons.

Provide the title-bar height as a `CompositionLocal` and have screens offset their
top content by it:

```kotlin
val LocalTitleBarInset = compositionLocalOf { 0.dp } // 0 on platforms w/o a custom bar

val titleBarInset = when {
    isMacOS -> 28.dp
    (isWindows || isLinux) && titleBarActive -> 32.dp   // == setHeight
    else -> 0.dp
}
CompositionLocalProvider(LocalTitleBarInset provides titleBarInset) { App() }
```

Rules of thumb:
- Background/gradient: full-bleed to the top (draw before/under the inset).
- Top bars, and any top-corner icons/actions: pad down by `LocalTitleBarInset`.
- Put this inset in **one** place per screen. It's easy to end up applying it in a
  top-bar component *and* a per-screen padding *and* a content padding and get
  double/triple gaps. Centralize it (e.g. a single `ScreenTopSpacer` primitive that
  every screen uses) rather than sprinkling `LocalTitleBarInset.current` everywhere.
- Remember the corner asymmetry: a top-**right** action overlaps Windows buttons;
  a top-**left** one overlaps macOS traffic lights.

---

## Checklist for a new app

- [ ] `Window {}` with the native frame (NOT `undecorated`).
- [ ] macOS: the three `apple.awt.*` rootPane properties + opaque fallback bg.
- [ ] Windows/Linux: reflective JBR `CustomTitleBar`, `setHeight`, overload-matched
      `setCustomTitleBar`; degrade gracefully off JBR.
- [ ] Bundle JBR in the Windows/Linux distribution.
- [ ] Activation flag backed by `mutableStateOf` (Trap 2).
- [ ] Windows drag strip via `onPointerEvent` + `forceHitTest(false)`, gated to
      `isWindows` (Trap 1).
- [ ] `LocalTitleBarInset` provided; top content inset by it, background full-bleed.
- [ ] Test on the platform you're NOT developing on — the mechanisms differ and
      bugs are platform-specific (dragging, button overlap).

## Reference implementation

`player/desktop/src/desktopMain/kotlin/com/spela/player/desktop/Main.kt`
(`applyJbrTransparentTitleBar`, the drag strip, `titleBarInset`) and
`LocalTitleBarInset.kt`.
