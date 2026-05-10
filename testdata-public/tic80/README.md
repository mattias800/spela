# TIC-80 PD test cart

**Status:** placeholder — folder exists but no `.tic` cart shipped yet.

## What's needed

A small `.tic` cart that boots the libretro `tic80` core and produces frames,
so the E2E scanner has at least one playable game in the TIC-80 library.

## Sourcing

The TIC-80 project (https://github.com/nesbox/TIC-80, MIT) ships demo
cartridge **sources** under `demos/` (Lua / JS / etc.) but no
precompiled `.tic` binaries. Generating one requires the TIC-80
editor:

```sh
tic80 --cmd "load luademo.lua, save tic80-demo.tic, exit"
```

Alternatively, the TIC-80 community at https://tic80.com hosts
hundreds of MIT/CC0-licensed carts. Pick a small one that boots the
core, save it here as e.g. `tic80-demo.tic`, and add an entry in
`../ATTRIBUTION.md` with author + license.
