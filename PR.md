# Linux desktop bring-up: six fixes from a day of on-device testing

First real-world testing round of the desktop player on Linux (Bazzite/Fedora,
KDE Plasma 6 on Wayland, AMD Strix Halo). Every issue below was root-caused on
the machine — core dumps, disassembly, and cross-referencing the cores' source —
and verified fixed by replaying the failing scenario. Linux was the last
untested desktop platform; macOS, Windows, and Android behavior is explicitly
preserved by every change (each fix is Linux-conditional or a strict
correctness fix).

## 1. GameCube (Dolphin) froze and aborted seconds into gameplay

`hs_err` signature: `SIGSEGV, si_code SI_TKILL` from our own pid, pc in
innocent compiled-Java code. JIT-fastmem cores (Dolphin, PPSSPP) install their
own SIGSEGV handlers, replacing HotSpot's. Dolphin's handler re-raises foreign
faults via `raise()` (verified in `MemTools.cpp`), which strips `si_addr` — so
the JVM aborts on its own routine safepoint-poll faults.

Linux is the only platform with no handler arbitration (Windows VEH chains by
design, Android ART ships `libsigchain`, macOS uses Mach exception ports).
**Fix:** preload HotSpot's own chaining shim `libjsig.so` — same mechanism as
Android's sigchain. Wired Linux-only: dev `:desktop:run` env + a launcher
wrapper baked into `createDistributable` (AppImage/Flatpak exec it, so every
artifact inherits the preload). Fastmem stays enabled at full speed. Full
postmortem added to `player/native/CORE_HOST.md`.

## 2. PSP (PPSSPP) black screen, then native crash

Two stacked causes: Mesa's Wayland EGL platform has no pbuffer support (GL HW
render never initialized), and once past that, PPSSPP's bundled GLEW is
GLX-built — `glewInit()` fails in any EGL context and PPSSPP then calls
`CreatePresets()` on a null draw context.

**Fix:** GLX (pbuffer) is now the primary Linux GL context — matching
RetroArch's X11 driver, the environment GL cores are de-facto built against —
with EGL (pbuffer, then surfaceless) kept as headless fallback. All dlopen'd,
no new link deps.

## 3. UI rendered at 1.0× on a 1.5×-scaled desktop, window resize didn't relayout

AWT's device scale on Linux/X11 is **integer-only** — fractional
`sun.java2d.uiScale` / `GDK_SCALE` are floored by both Temurin and JBR
(verified empirically). KDE publishes the true fractional scale over XSETTINGS
(`Gdk/UnscaledDPI`), which nothing in the AWT stack reads.

**Fix:** read the XSETTINGS desktop properties, compute the target scale, and
override `LocalDensity` at the Compose window root when AWT got it wrong —
Compose density has no integer limitation. No-ops on GNOME integer scaling and
when XSETTINGS is absent. Pure decision logic is unit-tested (KDE fractional,
GNOME 2×, GNOME text-scaling, missing/corrupt values).

The resize-relayout bug was Temurin-specific; bundling JBR fixes it (see 6).

## 4. Azahar (3DS) crashed on save-state + quit/resume — use-after-free

Core-dump forensics: Azahar's `Vulkan::Scheduler` worker called
`vulkan_intf->lock_queue` through our `retro_hw_render_interface_vulkan` after
the memory was freed and partially reused (garbage-RIP jump). Two ordering
bugs:

- The emulation screen's `onDispose` fired `nativeGpuDeinit` while the async
  stop flow (auto-save → upload → unload) was still running — destroying the
  VkDevice and freeing the renderer struct under the live core. Slow save
  uploads made the race always lose. **Fix:** GPU deinit is deferred while a
  core is loaded and runs at the end of `nativeDeinit` on the emulation thread.
- `nativeUnloadGame`/`nativeDeinit` destroyed our VkDevice before
  `retro_unload_game`/`retro_deinit`, but the core's GPU threads are only
  joined inside core shutdown (Azahar's `context_destroy` doesn't stop its
  scheduler). A 200 ms grace sleep was standing in for a join. **Fix:**
  device destruction moved after core teardown, matching RetroArch's order.

A cheap `[HwIfaceCanary]` integrity check is deliberately left in as a
regression tripwire (marked TEMPORARY in code).

## 5. Core auto-update crashed the dynamic linker (SIGBUS)

The updater extracted the downloaded `.so.zip` **over the live core file in
place**. Cores can stay mapped even after dlclose (RTLD_NODELETE/static TLS);
rewriting the inode under a mapping makes `ld-linux` SIGBUS
(`_dl_lookup_map`). **Fix:** extract to a temp sibling + atomic rename
(desktop + Android), mirroring the existing `atomicWriteFile` pattern.

Known follow-up (tracked separately, not in this PR): the staleness check
compares the local sha against the server's poll-time buildbot snapshot but
redownloads from buildbot's current "latest", so the hashes may never converge
and every launch redownloads. Proper fix: download via the existing
`downloadCoreByHash` endpoint so the client converges with the server's
fingerprinted binary.

## 6. CI: desktop-build.yml now bundles JBR on Linux/Windows (matching release.yml)

release.yml already used JBR; desktop-build artifacts were built with Temurin
and behaved differently (broken resize relayout, no scale detection on Linux).
macOS stays on Temurin (no patches needed; avoids GitHub API rate limits).

## Verification

- Full desktop test suite green (`:shared:desktopTest` + `:desktop:desktopTest`),
  including new unit tests for the density-override logic.
- On-device (Linux/Wayland/KDE 150%): Mega Drive (SW core), Wind Waker
  (Dolphin/Vulkan + fastmem), Burnout (PPSSPP/GL via GLX), OoT 3D
  (Azahar/Vulkan) — all boot, render at correct scale, play at full speed, and
  survive save → quit → resume cycles, including a core re-download mid-cycle.
- macOS/Windows/Android: no behavioral changes (all fixes Linux-gated or
  strict correctness fixes verified to preserve existing ordering).

## Notes for reviewers

- The jpackage launcher wrapper renames `bin/Spela` → `bin/Spela-bin` and
  installs a shell script in its place (jpackage resolves its `.cfg` by
  binary basename, so the cfg is duplicated). AppImage/Flatpak/deb launch
  paths all exec `bin/Spela`.
- Two pre-existing gaps surfaced during this work, worth their own issues:
  `packageDeb` doesn't run the `createDistributable` post-processing (its
  artifacts lack the native-lib path patch entirely), and the
  `createDistributable` `doLast` skips when the task is up-to-date, which can
  leave a stale native lib in local builds.
- Quota-blocked auto-save uploads silently lose progress with only a
  one-frame error flash — separate UX issue, being filed.
