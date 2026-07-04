# Sharp X68000 PD test ROMs

**Status:** zero-byte filename stubs only. No launchable X68000 test ROM
ships yet.

## Filename stubs

The files currently in this folder are zero-byte fictional Spela-branded
stubs. They exist only so scanner/library UI flows can index X68000-like
titles in fresh installs and CI. They are not launchable ROMs and do not
exercise the `px68k` core.

## What's needed

A small `.dim`, `.img`, `.d88`, or `.88d` file that boots the libretro
`px68k` core and produces frames.

Tracking issue: #1640.

## Caveat: BIOS required

Unlike most other platforms in this directory, X68000 emulation also
needs the system BIOS files (`IPLROM30.DAT`, `CGROM.DAT`). Without
them, no cart loads — including this PD test ROM. The BIOS is
**not** redistributable and must be supplied by the operator. CI
cannot boot-test X68000 until the BIOS is provisioned.

## Sourcing

PD demoscene productions released under CC0 are the cleanest source.
The Japanese demoscene scene that targeted X68000 has a number of
titles distributed under CC0 / public-domain terms.

When a ROM is added, also note in `../ATTRIBUTION.md` that the
platform's E2E coverage is gated on the operator providing the BIOS.
