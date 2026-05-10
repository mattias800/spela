# Public-domain test ROMs

This directory contains a small set of public-domain / freely-redistributable
ROMs used by the CI test suite. Unlike `testdata/` (which is `.gitignore`d
and intended for the user's own legally-acquired ROM library), these ROMs
ship with the repository so the GitHub Actions Android E2E workflow has
something to seed the library with.

ROMs here are **not** commercial titles. They're emulator-developer test
ROMs and homebrew. The selection criteria are:

- **Boots a libretro core successfully** — produces frames so the test
  helper's `waitForGameRunning` semantic-marker assertion passes.
- **Tiny** — every KB committed to git stays in every clone forever.
- **Clearly redistributable** — by stated license or by long-standing
  community convention.

If you spot something here that shouldn't be redistributed, file an
issue and we'll yank it.

## Contents

### Platform folders without ROMs yet

`amstradcpc/`, `x68000/` — these directories ship with a `README.md`
documenting how to source a PD ROM but no ROM file yet. The backend
seeds the corresponding consoles; CI's library scan finds zero games
for them until a ROM lands. (For X68000 a ROM alone won't help: the
platform also needs operator-supplied IPLROM30 + CGROM BIOS.)
Tracked under #1159.

### `tic80/spela-hello.tic`

| Field | Value |
|-------|-------|
| **Author** | Spela contributors (this repo) |
| **Year** | 2026 |
| **Size** | 193 bytes |
| **Source** | hand-written; see `scripts/build-testdata-roms.py` |
| **License** | CC0 / Public Domain — dedicated to the public domain by the authors |

A minimal TIC-80 cartridge containing a single Lua `TIC()` function
that prints `SPELA TIC-80 OK` plus a frame counter. Built directly to
the documented `.tic` chunk format (one CODE chunk, header
`0x05 NN NN 00`, no compression, no sprites/sound). Boots the
libretro `tic80` core to confirm the platform integration works.

### `zxspectrum/spela-hello.tap`

| Field | Value |
|-------|-------|
| **Author** | Spela contributors (this repo) |
| **Year** | 2026 |
| **Size** | 61 bytes |
| **Source** | hand-written; see `scripts/build-testdata-roms.py` |
| **License** | CC0 / Public Domain — dedicated to the public domain by the authors |

A minimal Sinclair ZX Spectrum tape image containing a one-line
BASIC program: `10 BORDER 2 : PAPER 2 : CLS : PRINT "SPELA OK"`.
Built directly to the documented `.tap` block format (one header
block + one data block, autostart at line 10). Boots the libretro
`fuse` core's auto-load-tape flow to confirm the platform
integration works.

### `nes/nestest.nes`

| Field | Value |
|-------|-------|
| **Author** | kevtris (Kevin Horton) |
| **Year** | 2004 |
| **Size** | 25 KB |
| **Source** | https://www.qmtpro.com/~nes/misc/nestest.nes |
| **License** | No formal license. Freely redistributable per emulator-development community convention. |

`nestest.nes` is the canonical NES emulator-accuracy test ROM. It has
been mirrored, redistributed, and used as a regression-test fixture in
**every major open-source NES emulator** since its release — Nestopia
(the core Spela ships), Mesen, FCEUX, puNES, etc.

We don't use it for accuracy testing. We just need *a NES ROM that
boots and produces frames* in CI. nestest does that, is well-attested
upstream, and is small.
