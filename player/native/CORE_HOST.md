# Out-of-process core host & debugging libretro core crashes (desktop)

This documents `spela-core-host` — a standalone native harness for loading and
running a libretro core **outside** the JVM — and the workflow it enables for
diagnosing core/integration crashes on desktop. It also records the **env
command-number masking pitfall** and the **#1237 / #1243 Azahar/3DS postmortem**,
because that bug is the canonical example of what this tooling is for.

> TL;DR for the impatient: if a libretro core crashes on desktop, build the
> native bridge, run `spela-core-host.exe` under `cdb` against the same ROM, and
> you get a fast, JVM-free, fully-symbolized repro. See
> [Debugging a core crash](#debugging-a-core-crash-with-cdb).

---

## Why it exists

The desktop player normally drives the libretro bridge **in-process via JNI**
(`LibretroJni` → `libretro_bridge.c`). That's fine, but it makes core crashes
hard to debug: the JVM's own exception handler turns the fault into an
`hs_err_pid*.log` that **cannot symbolize native frames**, and the process is
full of JVM threads, GC, and JIT noise.

`spela-core-host` loads the **same bridge code** (`sp_host_*` API, see below) in
a **plain native process** with a **PDB**, so:

- a crash gives a **fully-symbolized** native stack under `cdb`/WinDbg;
- there's no JVM to muddy signals/threads;
- it independently answers "is this the core, our frontend, or the hosting?" —
  if it crashes here too, the JVM is **not** the cause.

It is a **debug/isolation harness**, not a shipping feature. It is desktop-only;
Android always runs the core in-process (and doesn't have this tooling).

---

## Architecture

```
parent process                          spela-core-host.exe (native, no JVM)
  (or a human + cdb)                       loads spela-libretro bridge code
        │                                  loads the real core (azahar, …)
        ├─ memory-mapped file ◀── BGRA framebuffer + audio + header
        └─ (header fields)    ──▶ input state + should_stop
```

- **`sp_host_*` API** (`spela_host_api.h`, implemented at the end of
  `libretro_bridge.c`): non-JNI entry points mirroring the JNI surface —
  `sp_host_load_core / init / gpu_init_offscreen / load_game / run_frame /
  render_to_bgra / get_audio / set_button / …`. They reuse the *exact* internal
  core/video/audio/input/GPU machinery the JNI path uses.
- **`spela_core_host.c`**: a minimal native frontend `main()` — parses args,
  maps the shared file, runs the emulation loop, publishes frames/audio, reads
  input, stops on request.
- **IPC** (`spela_host_ipc.h`): a memory-mapped file — a fixed 256-byte header
  (`SpHostHeader`) + a BGRA video buffer + an audio buffer. Chosen because both
  a JVM (`FileChannel.map`) and native code (`CreateFileMapping`/`mmap`) can
  share it. Sync is the `frame_counter` (host increments after each frame).

**JVM-free guarantee.** The host compiles `libretro_bridge.c` with
`-DSPELA_CORE_HOST`, which `#ifdef`s out the entire JNI bindings section. So the
host links **no** `jvm`/`jawt` libraries. `jni.h`/`jawt.h` are still on the
include path (for the unused `JavaVM*` type), but nothing references JVM symbols
at link time.

---

## Building

The host builds automatically with the native library:

```
# from player/ — the desktop env (MSVC dev shell, JAVA_HOME=JBR, Vulkan SDK,
# SDL2, cmake on PATH) must be set up; run-desktop.ps1 shows the full setup.
./gradlew :desktop:buildNativeLibrary
```

`cmake --build` builds every target, so `spela-core-host.exe` lands next to
`spela-libretro.dll` in `player/desktop/build/native/`.

---

## Running

```
spela-core-host.exe \
  --core   <path to *_libretro.dll> \
  --game   <path to ROM> \
  --system <BIOS/system dir> \
  --save   <save dir> \
  --shm    <path to a backing file the parent created at SP_TOTAL_SIZE bytes> \
  [--width N --height N]        # offscreen render size (default 256x224) \
  [--var key=value ...]         # core options, repeatable
```

The parent must create the `--shm` file at `SP_TOTAL_SIZE`
(`256 + 16 MiB + 256 KiB ≈ 16.25 MiB`) before launching. The host writes the
header magic when ready, then increments `frame_counter` each produced frame and
sets `video_width/height` + the BGRA bytes; the parent reads frames when the
counter changes and writes `input_buttons`/analog/pointer + `should_stop`.

> Note on reading the header from another tool: the C struct aligns the
> `uint64 frame_counter` to **offset 24**, not 20 (4 bytes of padding after the
> five `uint32`s). Mirror the real C offsets, not the field order.

### Phase 1 scope

This is the minimum that proved out the fix. Notably **not** done yet: a JVM
client wiring the host into the actual app (the in-process path is the shipping
path), full software-render pixel-format conversion (Vulkan HW-render readback
via `render_to_bgra` is the tested path), save states, shaders, fast-forward.

---

## Debugging a core crash with cdb

`cdb` ships with WinDbg: `winget install Microsoft.WinDbg` →
`%LOCALAPPDATA%\Microsoft\WindowsApps\cdbX64.exe`.

1. Create the shm file at `SP_TOTAL_SIZE` bytes.
2. Put the core + a ROM somewhere; run the host under cdb. A command file
   (`-cf`) avoids shell-quoting hell. Example that breaks on the fatal
   first-chance access violation, dumps, and quits — while passing through the
   benign first-chance AVs a JVM-hosted process would generate (harmless here
   since the host is JVM-free, but the pattern generalizes):

   ```
   sxe -c ".if (@rip == 0x<crashpc>) { .echo CRASH; r; kb 60; lm; qd } .else { gn }" av
   g
   ```

   For a *data* corruption (a global written wrong), a **hardware
   write-watchpoint** is gold:

   ```
   ba w8 0x<addr> ".echo WRITE; r; ub @rip L12; kb 20; .echo ---; g"
   ```

   That's how #1243 was cracked: the watchpoint on the garbage global showed the
   writer; disassembling the call target revealed it was **our own
   `environment_callback`**, called with an ordinal that decoded the bug (below).

3. Symbols: the host has a PDB, so bridge frames symbolize. The core
   (`azahar_libretro.dll` etc.) is a stripped MinGW build — frames show
   `module+offset` and nearest-export names (often misleading); rely on the
   bridge frames + register/memory inspection.

---

## ⚠️ The env command-number masking pitfall

`environment_callback` does `unsigned base_cmd = cmd & 0xFFFF;` so that
**EXPERIMENTAL** commands (`cmd | 0x10000`) match plain `case` labels — e.g.
`GET_HW_RENDER_INTERFACE` is `41 | 0x10000` and we want it to hit `case 41`.

**The trap:** our `libretro.h` is trimmed and defines some **local** constants
with plain low numbers. If any EXPERIMENTAL command's low 16 bits collide with
such a constant, the masked command lands on the **wrong** `case`. A `GET_*`
command that returns an interface/struct via an out-param is then answered with
a `case` that returns `true` **without writing the out-param** → the core uses
uninitialized memory.

When adding env handling: **handle experimental `GET_*` commands by their full
value, before the mask** (as the `GET_SENSOR_INTERFACE` guard now does), and be
suspicious of any low-numbered local constant that could alias `0x100xx & 0xFFFF`.

---

## Postmortem: #1237 / #1243 — Azahar (3DS) crash on launch

**Symptom.** Launching a 3DS game crashed on desktop with
`EXCEPTION_ACCESS_VIOLATION` deep in `retro_run` — an indirect call through a
garbage function pointer (`call rax`, non-canonical address). Worked on Android;
worked in standalone RetroArch with the *identical* core + ROM.

**Root cause.** A command-number collision (the pitfall above). Our trimmed
`libretro.h` defines `RETRO_ENVIRONMENT_SET_CONTROLLER_PORT_DEVICE_ENV = 25`. The
real `RETRO_ENVIRONMENT_GET_SENSOR_INTERFACE` is `25 | EXPERIMENTAL = 0x10019`,
which masks to `25` and matched the controller-port case — returning `true`
**without** filling the caller's `retro_sensor_interface`. Azahar stored the
uninitialized `{set_sensor_state, get_sensor_input}` pointers and later called
the garbage `set_sensor_state(port, ENABLE, rate)` (the crash's `r8 = 0x3c` = 60
= the sensor event rate confirmed it). On Android the uninitialized stack
happened to be zero, so Azahar's null-guard hid it; on desktop the stack garbage
was non-zero, so it crashed every launch.

**Fix** (`environment_callback`, see #1246): intercept `GET_SENSOR_INTERFACE` by
its full value before masking and decline cleanly — the core keeps null sensor
callbacks; motion is disabled (we expose no host sensors); no crash.

**Why the investigation took so long — dead ends ruled out, in order:**
firmware/system files, graphics backend (GL ≡ Vulkan, both crashed identically),
CPU JIT (interpreter crashed too), thread stack size (256 MB, no change), memory
arena/address-space collision, DLL relocation + Crypto++ integrity (normal-ASLR
false positives present in RetroArch too), JVM `-Xrs`, OBS/Wallpaper-Engine
graphics hooks, and missing/delay-loaded dependencies.

The "works in RetroArch, crashes in Spela" signal pointed (wrongly) at the
in-process JVM hosting — until `spela-core-host` (a clean native process running
**our** bridge) crashed **identically**, proving the hosting was not the
variable; our frontend was. cdb on the harness then walked from the garbage
global to the writer to the dispatcher — which turned out to be our own
`environment_callback` — and the ordinal `0x10019` named the bug.

**Lesson:** when a core works in another frontend but not ours, the difference is
in **our libretro frontend behavior**, not necessarily the process/host. Reach
for `spela-core-host` early.
