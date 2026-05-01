# ScummVM web experiment — autonomous-session notes

> Experimental branch: `experiment/scummvm-web` (issue #794). Not for
> merge as-is. This file captures what works, what doesn't, and what
> the next person should look at.

## Status snapshot

| Layer | Status |
|---|---|
| Server schema (`Console.WebEmulator`) | ✅ added, seeded, backfilled, surfaced in DTO |
| Server tests | ✅ green (`go test ./internal/api/... ./internal/db/...`) |
| Web type-check | ✅ green (`tsc -b`) |
| Web unit tests | ✅ 1561 / 1561 pass |
| Web build | ✅ green |
| Play page routing | ✅ ScummVM consoles route to `/scummvm/scummvm.html` |
| Iframe shell loads + posts `emulator-ready` | ✅ verified via Playwright |
| Init protocol (`init` → `rom-load-progress` → `game-started`) | ✅ verified |
| Game `.scummvm` tar download (Spela auth) | ✅ via vite `/api` proxy |
| Tar parsing + MEMFS mount at `/games/<gameId>/` | ✅ |
| Marker-file gameid resolution (.scummvm contents) | ✅ |
| WASM bootstrap | ✅ scummvm.js + scummvm.wasm load |
| ScummVM **launcher** renders in canvas | ✅ — see `scummvm-launcher.png` |
| `/data/` tree proxy (gui-icons.dat, themes, fonts) | ✅ via vite `/scummvm-data` + regex `/scummvm/+data` proxies |
| Engine plugin `dlopen` (libagi.so, libsci.so, …) | ❌ **blocker** — symbol lookup fails |
| Game launch | ❌ blocked by plugin failure |
| Save state round-trip | ⏸️ phase 3, deferred |

## What's vendored, what's proxied, what's our code

- `web/public/scummvm/scummvm.js` (9.1 MB) — vendored verbatim from
  scummvm.kuendig.io (Nov 2025 deployment)
- `web/public/scummvm/scummvm.wasm` (37 MB) — same
- `web/public/scummvm/{manifest.json,scummvm-{192,512}.png,logo.svg,favicon.ico}` — same
- `web/public/scummvm/scummvm.html` — written for Spela. Custom shell
  with the parent ↔ iframe postMessage protocol. Replaces upstream's
  `custom_shell.html`.
- `web/public/scummvm/shell.js` — written for Spela. Tar untar +
  MEMFS mount + lifecycle events + URL rewriting for `/data/` paths.
- `web/public/scummvm/VENDOR.md` — vendor docs.
- vite proxy:
  - `/scummvm-data/*` → `https://scummvm.kuendig.io/data/*` (for our
    own absolute-path rewrites)
  - regex `^/scummvm/+data/.*` → `https://scummvm.kuendig.io/data/*`
    (for the upstream build's page-relative resolution; matches both
    `/scummvm/data/...` and `/scummvm//data/...`)

## The plugin-dlopen blocker — diagnosis

Every plugin `dlopen` fails with:

```
WARNING: Failed loading symbol 'PLUGIN_getVersion' from plugin
'/data/plugins/libagi.so' (Failed loading PLUGIN_getVersion: Tried to
lookup unknown symbol "_PLUGIN_getVersion" in dynamic lib:
/data/plugins/libagi.so)!
```

What we've ruled out:

- **Bytes are identical to upstream.** `curl -sI` against both our
  proxy and `scummvm.kuendig.io/data/plugins/libagi.so` returns the
  same `Content-Length` (1687408), same `Content-Type`
  (`application/wasm`), same `ETag`. So the proxy isn't mutating the
  binary.
- **The HTTP fetch itself succeeds.** Network log shows `200 OK` on
  every plugin request. ScummVM loads the binary, invokes `dlopen`,
  the binary instantiates — only the *symbol resolution* fails.
- **The upstream deployed site does work** with the same plugins.

Suspect: the underscore prefix on `_PLUGIN_getVersion`. Modern
Emscripten doesn't add a leading underscore for C symbols, but the
host (the main scummvm.wasm) does. Possible mismatch between how the
plugin .so files were compiled vs how the host is querying them. But
this would equally fail upstream, so something about our environment
must differ.

Theories worth chasing:

1. **dlopen relies on a runtime helper that the upstream `scummvm.html`
   sets up but ours doesn't.** The deployed shell is minified and the
   Module setup hooks I copied may be missing one. Diff the deployed
   `<script>` block character-by-character against our shell's Module
   construction.
2. **Asyncify's synchronous-XHR pattern returns the bytes via a
   different code path than `WebAssembly.compile`.** Plugin .so files
   in this build are wasm modules loaded via dlopen → which internally
   may do `WebAssembly.compile(arrayBuffer)` — and the binary needs
   to be passed through an exact byte path that XHR-via-Asyncify might
   not satisfy. Try using `FS.createPreloadedFile` or
   `FS.createDataFile` to bake plugins into MEMFS directly.
3. **dlopen looks up the .so by Emscripten's own internal "DSO id"
   table that the build pre-populates with paths from a manifest.**
   If the build expects an exact path like `data/plugins/libagi.so`
   relative to a known cwd, and our cwd or path-resolution differs,
   dlopen would load the file but then fail symbol lookup against
   the wrong DSO.
4. **The .so is intended to be served with `Content-Type:
   application/octet-stream`** rather than `application/wasm`, and
   the upstream LiteSpeed config does the right thing while our vite
   proxy passes through the upstream's mistakenly-set `application/
   wasm` type. Plausible but unverified — would need to test by
   rewriting Content-Type on the proxy.

The cheapest verification: clone the deployed page entirely (download
their `scummvm.html` verbatim, NOT our custom one) and load *it* in
our iframe pointing at our own game data. If that works → our shell
or proxy is the issue. If that also fails → some browser-level thing
about the sandboxed iframe context.

### Update 1: tested upstream's "Beneath a Steel Sky" → also fails

Initial test on `https://scummvm.kuendig.io/` clicking **Start** on
Beneath a Steel Sky → *"Error running game: Path does not exist"*.
Hypothesised that upstream's deployment doesn't run games either.

### Update 2: that was wrong — upstream DOES launch games

Re-tested with **Full Throttle (Demo/Windows/English)**. **It runs
end-to-end** — the iconic desert-highway opening renders, no console
errors. So the working state of the upstream deployment is real;
my first sample was a poor pick (Beneath a Steel Sky's bundled data
is broken, but Full Throttle's isn't).

### The actual root cause: static-vs-dynamic engine linkage

Critical evidence in the upstream debug log around the Full Throttle
launch:

```
[LOG]  User picked target 'ft-demo' (engine ID 'scumm', game ID 'ft')...
[DEBUG] Starting fetch #26 ... ft-dos-demo-en/index.json ...
[DEBUG] Starting fetch #27 ... ft-dos-demo-en/FT.000 ...
[DEBUG] Starting fetch #32 ... ft-dos-demo-en/FT.001 ...
```

**No `/data/plugins/lib*.so` fetches happen at any point.** The
build statically links the SCUMM engine into `scummvm.wasm`, so
launching a SCUMM game (Full Throttle, Curse of Monkey Island, Day
of the Tentacle, …) never touches dlopen.

Our experiment with `sq2` (AGI engine) hit the dlopen path because
AGI is **not** statically linked. The build's `data/plugins/index.json`
lists every engine as a dynamic plugin, but the actual runtime
behaviour is: try static first; if no static engine claims the game
ID, fall back to dlopening every plugin to ask. That fallback is
what's broken in this build — `_PLUGIN_getVersion` symbol resolution
fails for every `.so`. Same wall would hit upstream if they tried to
launch BASS (Sky engine), Lure of the Temptress (Lure engine), etc.
(That's likely why those entries are greyed out in their launcher —
they know dlopen is broken.)

### What this means for #794

**Our shell is functionally complete for the SCUMM engine.** Any
LucasArts adventure (Maniac Mansion, Day of the Tentacle, Sam &
Max, Full Throttle, The Dig, Curse of Monkey Island, Indiana Jones,
…) should launch end-to-end with no further changes — the path-mount
via tar works, scummvm.wasm boots, the SCUMM engine is statically
linked, no dlopen needed.

To verify: stage a SCUMM-engine `.scummvm` game in
`testdata/roms/scummvm/<id>/` (e.g. download chkuendig's bundled
Full Throttle demo from `https://scummvm-data.kuendig.io/ft-dos-demo-en/`,
package as a tar, drop in testdata, scan), then launch via the
play page. Spela's testdata currently only has non-SCUMM games (sq2
AGI, kyra3 Kyra, dw2 Tinsel, etc.) — none of which are statically
linked.

To support all engines (the eventual goal), three options:

1. **Rebuild scummvm.wasm with `--disable-plugins`** so all engines
   are static. Output ~80–120 MB but eliminates dlopen entirely.
   Reproducible via the upstream build pipeline (`dists/emscripten/build.sh`)
   with that flag. Multi-hour build.
2. **File an upstream issue** asking why dlopen's `PLUGIN_getVersion`
   resolution fails. The same issue affects them — if they've worked
   around it for SCUMM but not for the other dynamic engines, they
   probably know about it.
3. **Compile libretro `scummvm` core to WASM** and graft into
   EmulatorJS — the multi-week path the umbrella called out. Avoids
   the chkuendig build entirely.

## The init / arguments timing issue

The upstream `custom_shell-pre.js` (inlined into the deployed
`scummvm.js`) does `Module["arguments"]=[]` early, then pushes from
the URL hash. Setting `Module.arguments = [...]` before loading
`scummvm.js` doesn't survive — the inlined pre-script wipes it.

Fix in `shell.js`: set `window.location.hash = "#--path=/games/<id>
<id>"` *before* injecting the script tag. The pre-script reads the
hash and populates `Module.arguments` from it. Verified working.

## Save state plumbing

Stubbed in `shell.js`. `request-save-state` and `load-save-state`
return an immediate error ("not implemented yet (#794 phase 3)") so
the parent's UI doesn't hang on a request that never resolves.

## What to do next

1. **Reproduce the plugin failure outside our shell.** Copy
   chkuendig's deployed `scummvm.html` byte-for-byte into
   `/scummvm/upstream-test.html`, point at our own data proxy, and
   load it. Result narrows down whether the failure is in our shell
   or in our environment.
2. If our shell is at fault, diff the inlined `Module` setup in the
   deployed `scummvm.js` against ours — every initial property,
   especially anything dlopen-related (`dynamicLibraries`, `INITIAL_MEMORY`,
   `ALLOW_MEMORY_GROWTH`, etc).
3. If the environment is at fault, instrument the dlopen code in
   `scummvm.js` (search for `getProcAddress`/`dlopen`/`mainModule`)
   to see which symbol-lookup table is being queried.

## Verification commands

```sh
# Server
source .env && go run ./cmd/server
# Web
cd web && npm run dev
# Probe
playwright-cli open http://localhost:5173/scummvm-probe.html
playwright-cli fill e5 "/api/games/<id>/download/<file>.scummvm?token=<TOKEN>"
playwright-cli click e10
# Inspect
playwright-cli eval "() => window.__scummvmProbe.events.slice(-15)"
playwright-cli console
playwright-cli network
```

The probe page (`web/public/scummvm-probe.html`) is a manual driver —
loads `/scummvm/scummvm.html` in an iframe, surfaces events to the
page, lets you trigger `init`. Safe to delete once we either ship or
abandon the experiment.
