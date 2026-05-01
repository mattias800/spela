# ScummVM web build — vendor notes

## Source

Files in this directory (except `scummvm.html`, `shell.js`, and this
file) are downloaded verbatim from chkuendig's deployed ScummVM
Emscripten build at <https://scummvm.kuendig.io>.

Upstream repo: <https://github.com/chkuendig/scummvm> (`emscripten`
branch).

## What's vendored vs. fetched at runtime

| Path | Source | Notes |
|---|---|---|
| `scummvm.html` | written for Spela | Custom shell with parent ↔ iframe postMessage protocol. Replaces upstream's `custom_shell.html`. |
| `shell.js` | written for Spela | Tar untar + MEMFS mount + lifecycle events. |
| `scummvm.js` | scummvm.kuendig.io (Nov 2025) | Emscripten-compiled ScummVM glue. |
| `scummvm.wasm` | scummvm.kuendig.io (Nov 2025) | The WASM module. |
| `manifest.json`, icons, `logo.svg`, `favicon.ico` | scummvm.kuendig.io | Branding / PWA metadata. |
| `data/<engine>.dat`, `data/plugins/*.so`, …  | **fetched live** from `https://scummvm.kuendig.io/data/` | Engine plugins + data files. `shell.js` installs a `fetch` interceptor that redirects any `/data/...` request to upstream. |
| `home/web_user/*` | IndexedDB (IDBFS) | Per-browser ScummVM saves. |

The data tree (~50–100 MB depending on which engines were enabled at
build time) is left on chkuendig's CDN for the experiment to keep the
vendored artefact small. If/when this graduates to a self-hosted
asset, mirror `https://scummvm.kuendig.io/data/` into
`web/public/scummvm/data/` and remove the fetch interceptor from
`shell.js`.

## License

ScummVM is GPLv3. Source is at the upstream repo above. Spela ships
the WASM build alongside the rest of the player; it's a separate
binary blob, not a derivative work.

## Rebuild from source

If we promote this beyond an experiment, follow chkuendig's CI
workflow (`.github/workflows/main.yml` in chkuendig/scummvm-demo) to
reproduce the build:

```sh
git clone --recursive https://github.com/chkuendig/scummvm.git
cd scummvm
EMSDK_VERSION=4.0.15 dists/emscripten/build.sh build \
  --enable-release --enable-plugins --enable-all-engines \
  --default-dynamic --enable-cloud
# output appears in build-emscripten/ — copy to web/public/scummvm/
```

Caveats:
- emsdk install is ~1 GB.
- Cold-cache full build is several hours.
- Native libs (a52, faad, fluidlite, freetype2, fribidi, gif, jpeg,
  mad, mikmod, mpcdec, mpeg2, ogg, png, retrowave, theoradec, vorbis,
  vpx, zlib) take a chunk of that.

## Carve-out from CLAUDE.md rule #4

Rule #4 says "libretro only — no custom emulation code." That stands
for the native player. The web player needs an in-browser ScummVM,
and there's no libretro WASM core today, so this directory is the
explicit carve-out. See #794.
