# Sharp X68000 PD test ROM

**Status:** placeholder — folder exists but no ROM shipped yet.

## What's needed

A small `.dim`, `.img`, `.d88`, or `.88d` file that boots the libretro
`px68k` core and produces frames.

## Caveat: BIOS required

Unlike most other platforms in this directory, X68000 emulation also
needs the system BIOS files (`IPLROM30.DAT`, `CGROM.DAT`). Without
them, no cart loads — including this PD test ROM. The BIOS is
**not** redistributable and must be supplied by the operator. CI
will skip the X68000 scan path until the BIOS is provisioned.

## Sourcing

PD demoscene productions released under CC0 are the cleanest source.
The Japanese demoscene scene that targeted X68000 has a number of
titles distributed under CC0 / public-domain terms.

When a ROM is added, also note in `../ATTRIBUTION.md` that the
platform's E2E coverage is gated on the operator providing the BIOS.
