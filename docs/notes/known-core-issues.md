# Known Core Issues

This note tracks game/core problems where the likely fault line is inside a
libretro core, an upstream emulator, or a host integration boundary that needs
more evidence before changing Spela code.

Use this page to avoid repeating the same investigation. It is not a substitute
for a GitHub issue: every entry should link to the issue that owns the current
status and next steps.

## How To Classify A Report

Before changing Spela's audio, video, or pacing code, compare the same game and
core outside Spela:

1. Run the standalone upstream emulator when one exists.
2. Run RetroArch with the same libretro core and the same game data.
3. Try a different in-game audio/video backend when the game supports one.
4. Compare another game using the same Spela pipeline but a different core path.
5. Capture Spela logs for core sample rate, `retro_run` timing, render/present
   timing, and audio underruns.

If the issue reproduces in standalone or RetroArch, document the upstream
tracker link and keep the Spela issue as a reference. If it only reproduces in
Spela, treat it as a frontend integration bug.

## Open Investigations

### ScummVM: Maniac Mansion PC Speaker Note Timing

- **Issue:** #862
- **Status:** suspected upstream ScummVM/libretro-scummvm timing issue; not yet
  proven.
- **Symptom:** Maniac Mansion with PC speaker has uneven note lengths. Notes are
  not garbled, but the rhythm is inconsistent.
- **Signal:** Monkey Island with MT-32 sounds correct through the same Spela
  audio pipeline, which makes a general Spela resampler/pacing bug less likely.
- **Likely area:** early SCUMM PC speaker scheduling or the libretro wrapper's
  tick-to-samples conversion.
- **Next evidence needed:** run Maniac Mansion PC speaker in standalone ScummVM
  and in RetroArch with the same libretro core; compare Maniac Mansion AdLib and
  at least one other PC-speaker game.
- **Spela-side cheap checks:** log the requested core sample rate and compare
  `retro_run` timing for Maniac Mansion PC speaker, Maniac Mansion AdLib, and a
  known-good MT-32 game.

### PSP: PPSSPP FMV-Heavy Menu Stutter

- **Issue:** #1292
- **Status:** suspected PPSSPP media-engine decode regression or core-option
  issue; gameplay is otherwise smooth.
- **Symptom:** PSP gameplay holds 60 fps, but FMV-heavy menus can drop to about
  45 fps.
- **Signal:** instrumentation showed 60-84 ms spikes inside `retro_run`; audio,
  present, GPU, shader compilation, and frame pacing were ruled out.
- **Next evidence needed:** compare older PPSSPP core snapshots and test
  relevant PPSSPP core options for media-engine/CPU-clock/frameskip behavior.

### Windows Desktop: Mupen64Plus-Next N64 Crash Before Frame 1

- **Issue:** #1298
- **Status:** Windows-specific mupen64plus_next integration crash; not a recent
  Spela regression.
- **Symptom:** N64 crashes before frame 1 on Windows desktop. macOS, Linux, and
  Android have been verified separately.
- **Signal:** renderer, CPU core, GL profile, and core hot-swap hypotheses were
  ruled out. The failure points to a null table/function pointer during
  core/GFX/RSP initialization on Windows.
- **Next evidence needed:** symbolized mupen64plus_next build on Windows, run
  under `spela-core-host`/`cdb`, ideally with a hardware watchpoint on the null
  struct/table write path.
- **Related tooling:** `player/native/CORE_HOST.md`.

## Proven Integration Bugs

Move entries here only after the root cause is known and the Spela-side fix has
landed. Include the fixing PR, the exact frontend/core mismatch, and the
regression test or reproduction guard that prevents it coming back.

### Azahar / 3DS Sensor Interface Crash

- **Issues:** #1237, #1243
- **Fix:** #1246
- **Root cause:** `environment_callback` masked experimental environment command
  numbers with `cmd & 0xFFFF`. Azahar requested
  `RETRO_ENVIRONMENT_GET_SENSOR_INTERFACE` (`0x10019`), which collided with a
  locally defined low command number. Spela returned success without filling the
  sensor interface, so the core later called uninitialized function pointers.
- **Guardrail:** handle experimental `GET_*` commands by their full value before
  applying the low-16-bit mask.
- **Details:** see `player/native/CORE_HOST.md`.
