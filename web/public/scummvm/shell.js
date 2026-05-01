/* eslint-disable */
/**
 * Spela ScummVM iframe shell.
 *
 * Replaces upstream's custom_shell.html from chkuendig/scummvm so we
 * control the parent ↔ iframe postMessage protocol, the way game data
 * lands in the in-WASM filesystem, and the lifecycle events emitted
 * back to the play page.
 *
 * Wire format (matches web/src/lib/emulator-protocol.ts):
 *   parent → iframe : { type: "init", romUrl, scummvmGameId, ... }
 *                     { type: "pause" }, { type: "resume" }
 *                     { type: "request-save-state" }   (#794 phase 3 - TODO)
 *   iframe → parent : { type: "emulator-ready" }
 *                     { type: "game-started" }
 *                     { type: "rom-load-progress", loaded, total }
 *                     { type: "emulator-error", error }
 *
 * Filesystem layout inside Emscripten:
 *   /games/<scummvmGameId>/   MEMFS, populated from the tar fetched at
 *                             init.romUrl (Spela's standard download
 *                             endpoint serves .scummvm games as tar).
 *   /data/                    HTTP-backed fetches to chkuendig's CDN
 *                             for engine .dat / .so files. Vendoring
 *                             the entire 100 MB data tree is left as a
 *                             follow-up if/when this experiment graduates.
 *   /home/web_user/           IDBFS. Persists scummvm.ini + per-engine
 *                             saves between sessions on the same browser.
 */

(function () {
  "use strict";

  // Dev-mode points at the vite proxy at /scummvm-data/, which
  // forwards same-origin to the upstream chkuendig CDN. The iframe
  // CAN'T hit `https://scummvm.kuendig.io/data/` directly because of
  // CORS. Production swaps this for a mirrored static tree; see
  // VENDOR.md.
  const SCUMMVM_DATA_BASE_URL = "/scummvm-data/";
  const STATUS_EL = document.getElementById("status");
  const STATUS_TEXT_EL = document.getElementById("status-text");
  const PROGRESS_FILL_EL = document.getElementById("progress-fill");
  const CANVAS_EL = document.getElementById("canvas");

  function setStatus(text, percent) {
    if (text === null || text === undefined) {
      STATUS_EL.classList.add("hidden");
      return;
    }
    STATUS_EL.classList.remove("hidden");
    STATUS_TEXT_EL.textContent = text;
    if (typeof percent === "number") {
      PROGRESS_FILL_EL.style.width = Math.min(100, Math.max(0, percent)) + "%";
    }
  }

  function postToParent(message) {
    try {
      window.parent.postMessage(message, "*");
    } catch (err) {
      console.error("[scummvm-shell] failed to post to parent:", err);
    }
  }

  function postProgress(loaded, total) {
    postToParent({ type: "rom-load-progress", loaded, total });
  }

  function postError(error) {
    console.error("[scummvm-shell]", error);
    postToParent({ type: "emulator-error", error: String(error) });
  }

  /**
   * Minimal POSIX tar reader. The Spela `.scummvm` download endpoint
   * emits standard ustar format with directory + regular-file entries.
   * Keeps a single-file untar in JS so we don't pull a multi-MB
   * dependency into the shell.
   */
  function parseTar(buffer) {
    const view = new Uint8Array(buffer);
    const decoder = new TextDecoder("utf-8");
    const entries = [];
    let offset = 0;

    while (offset + 512 <= view.byteLength) {
      const header = view.subarray(offset, offset + 512);
      // All-zero block ends the archive.
      let allZero = true;
      for (let i = 0; i < 512; i++) {
        if (header[i] !== 0) {
          allZero = false;
          break;
        }
      }
      if (allZero) break;

      // Name is 100 bytes, optionally extended by prefix (ustar). We
      // read both and join — covers paths over 100 chars.
      const nameBytes = header.subarray(0, 100);
      const nameEnd = nameBytes.indexOf(0);
      const name =
        nameEnd === -1
          ? decoder.decode(nameBytes)
          : decoder.decode(nameBytes.subarray(0, nameEnd));
      const prefixBytes = header.subarray(345, 500);
      const prefixEnd = prefixBytes.indexOf(0);
      const prefix =
        prefixEnd === -1
          ? decoder.decode(prefixBytes)
          : decoder.decode(prefixBytes.subarray(0, prefixEnd));
      const fullName = prefix ? prefix + "/" + name : name;

      // Size is octal, 11 chars + NUL or space terminator.
      const sizeStr = decoder
        .decode(header.subarray(124, 124 + 12))
        .replace(/\0+$/, "")
        .trim();
      const size = parseInt(sizeStr, 8) || 0;

      const typeflag = String.fromCharCode(header[156]);
      offset += 512;

      if (typeflag === "5") {
        // Directory.
        entries.push({ name: fullName, type: "directory" });
      } else if (typeflag === "" || typeflag === "0" || typeflag === "\0") {
        // Regular file.
        const data = view.subarray(offset, offset + size);
        entries.push({ name: fullName, type: "file", data });
      }
      // Other typeflags (symlinks, hard links, longlinks) are skipped —
      // ScummVM games don't use them.

      // Advance by content + padding to next 512-byte boundary.
      offset += Math.ceil(size / 512) * 512;
    }
    return entries;
  }

  /**
   * Streaming download of [url] with progress reporting. Returns an
   * ArrayBuffer of the full body.
   */
  async function fetchTar(url) {
    setStatus("Downloading game…", 0);
    const response = await fetch(url, { credentials: "same-origin" });
    if (!response.ok) {
      throw new Error("download failed: HTTP " + response.status);
    }
    const totalHeader = response.headers.get("Content-Length");
    const total = totalHeader ? parseInt(totalHeader, 10) : 0;
    const reader = response.body.getReader();
    const chunks = [];
    let loaded = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      loaded += value.byteLength;
      if (total > 0) {
        setStatus(
          "Downloading game… " + Math.round((loaded / total) * 100) + "%",
          (loaded / total) * 100,
        );
      } else {
        setStatus("Downloading game… " + Math.round(loaded / 1024) + " KB");
      }
      postProgress(loaded, total);
    }
    // Concatenate chunks into one ArrayBuffer.
    const out = new Uint8Array(loaded);
    let pos = 0;
    for (const chunk of chunks) {
      out.set(chunk, pos);
      pos += chunk.byteLength;
    }
    return out.buffer;
  }

  /**
   * Mount the game tar into Emscripten's MEMFS at /games/<gameId>/.
   * Called from Module.preRun so the FS is hot before main() reads
   * the files.
   */
  function mountGameTar(FS, entries, gameId) {
    const root = "/games/" + gameId;
    try {
      FS.mkdirTree(root);
    } catch (e) {
      // mkdirTree throws if /games doesn't exist — create it explicitly.
      try {
        FS.mkdir("/games");
      } catch (e2) {}
      FS.mkdir(root);
    }

    for (const entry of entries) {
      // Strip a leading "<gameid>/" prefix that the Spela tar produces
      // (`tar -C games/<dir>` packs starting at the directory itself).
      // Falls through if no prefix is found.
      let rel = entry.name;
      if (rel.startsWith(gameId + "/")) {
        rel = rel.slice(gameId.length + 1);
      }
      if (!rel) continue; // top-level dir entry

      const absPath = root + "/" + rel;
      if (entry.type === "directory") {
        try {
          FS.mkdirTree(absPath);
        } catch (e) {}
      } else {
        // Ensure the parent directory exists.
        const lastSlash = absPath.lastIndexOf("/");
        const parent = absPath.slice(0, lastSlash);
        try {
          FS.mkdirTree(parent);
        } catch (e) {}
        FS.writeFile(absPath, entry.data, { encoding: "binary" });
      }
    }
  }

  /**
   * Hook the global fetch so that any request for `/data/...` is
   * redirected to chkuendig's CDN. ScummVM's HTTP-backed VFS makes
   * relative requests like `data/<file>.dat`, `data/index.json`, etc.
   * Vendoring the full data tree (~50–100 MB) is a follow-up if the
   * experiment graduates to a self-hosted artifact.
   *
   * Called once before scummvm.js is loaded.
   */
  /**
   * Path rewriter shared between the fetch and XHR interceptors. The
   * chkuendig build uses BOTH:
   *   - fetch() for the WASM binary
   *   - XMLHttpRequest (synchronous, Asyncify-driven) for engine data
   *     and plugin .so files served from /data/
   * Anything that resolves to `data/<x>` gets redirected to chkuendig's
   * CDN. https://... URLs pass through.
   */
  function rewriteUrl(url) {
    if (!url || typeof url !== "string") return url;
    if (/^https?:\/\//i.test(url)) return url;
    // Look for a `data/...` segment anywhere in the path. The upstream
    // build resolves data references against the iframe's base URL,
    // which produces variants like:
    //   data/index.json
    //   /data/plugins/libsci.so
    //   /scummvm/data/plugins/libsci.so      ← page-relative resolution
    //   /scummvm//data/plugins/libsci.so     ← double-slash artefact
    // All of them mean "fetch from the chkuendig data tree." Match the
    // first `data/` segment after collapsing leading slashes / duplicate
    // slashes / `./` prefix.
    const collapsed = url.replace(/\/+/g, "/").replace(/^\.\//, "");
    const idx = collapsed.indexOf("/data/");
    if (idx >= 0) {
      return SCUMMVM_DATA_BASE_URL + collapsed.slice(idx + "/data/".length);
    }
    if (collapsed.startsWith("data/")) {
      return SCUMMVM_DATA_BASE_URL + collapsed.slice("data/".length);
    }
    return url;
  }

  function installFetchInterceptor() {
    const originalFetch = window.fetch.bind(window);
    window.fetch = function (input, init) {
      let url;
      let rewritten;
      if (typeof input === "string") {
        url = input;
        rewritten = rewriteUrl(url);
        if (rewritten !== url) {
          return originalFetch(rewritten, init);
        }
      } else if (input && typeof input.url === "string") {
        rewritten = rewriteUrl(input.url);
        if (rewritten !== input.url) {
          return originalFetch(rewritten, init);
        }
      }
      return originalFetch(input, init);
    };

    // Wrap XMLHttpRequest.open so the synchronous-XHR-via-Asyncify
    // path used by ScummVM's HTTP-backed VFS hits the same upstream
    // CDN. Without this, /data/plugins/libagi.so etc. 404 against
    // our origin and the engine never loads.
    const OriginalXHR = window.XMLHttpRequest;
    function PatchedXHR() {
      const xhr = new OriginalXHR();
      const originalOpen = xhr.open.bind(xhr);
      xhr.open = function (method, url, async, user, password) {
        const rewritten = rewriteUrl(url);
        return originalOpen(method, rewritten, async, user, password);
      };
      return xhr;
    }
    PatchedXHR.prototype = OriginalXHR.prototype;
    PatchedXHR.UNSENT = OriginalXHR.UNSENT;
    PatchedXHR.OPENED = OriginalXHR.OPENED;
    PatchedXHR.HEADERS_RECEIVED = OriginalXHR.HEADERS_RECEIVED;
    PatchedXHR.LOADING = OriginalXHR.LOADING;
    PatchedXHR.DONE = OriginalXHR.DONE;
    window.XMLHttpRequest = PatchedXHR;
  }

  let initFired = false;

  async function handleInit(message) {
    if (initFired) {
      console.warn("[scummvm-shell] ignoring duplicate init");
      return;
    }
    initFired = true;

    const { romUrl, scummvmGameId: providedGameId, gameName } = message;
    if (!romUrl) {
      postError("init missing romUrl");
      return;
    }
    document.title = gameName ? gameName + " — Spela" : "Spela ScummVM";

    let tarBuffer;
    try {
      tarBuffer = await fetchTar(romUrl);
    } catch (err) {
      postError("failed to download game: " + err.message);
      return;
    }

    let entries;
    try {
      entries = parseTar(tarBuffer);
    } catch (err) {
      postError("failed to parse game tar: " + err.message);
      return;
    }
    if (entries.length === 0) {
      postError("game tar contained no entries");
      return;
    }

    // Resolve the ScummVM gameid. The .scummvm marker file's CONTENTS
    // is the canonical id ScummVM recognises (e.g. "monkey", "tentacle",
    // "sky"). The Spela seed README is explicit that the filename is
    // just a local label — `monkey1.scummvm` can contain `monkey`. Read
    // the marker if present; fall back to the provided id (which is
    // the filename basename) only if no marker exists.
    let scummvmGameId = providedGameId || "";
    const decoder = new TextDecoder("utf-8");
    for (const entry of entries) {
      if (entry.type !== "file") continue;
      if (!/\.scummvm$/i.test(entry.name)) continue;
      const text = decoder.decode(entry.data).trim();
      if (text) {
        scummvmGameId = text;
        break;
      }
    }
    if (!scummvmGameId) {
      postError("could not resolve ScummVM game id from .scummvm marker");
      return;
    }

    setStatus("Mounting game files…");
    installFetchInterceptor();

    // The upstream chkuendig pre-script (inlined into scummvm.js)
    // resets Module["arguments"] = [] very early — anything we set on
    // Module.arguments before loading the script gets stomped. Their
    // pre-script then reads window.location.hash and pushes each
    // space-separated token onto arguments. So we set the hash here
    // and let their pre-script do the work. Bypasses the launcher and
    // boots straight into the game.
    window.location.hash =
      "#--path=/games/" + scummvmGameId + " " + scummvmGameId;

    // Build the Module object before scummvm.js loads. Emscripten's
    // generated code uses the existing global Module if present,
    // and we set it up so:
    //   - preRun mounts the game files we already have in memory
    //   - canvas points at our visible <canvas>
    //   - print/printErr forward to console
    //   - onRuntimeInitialized hooks the lifecycle event back to the
    //     parent
    window.Module = {
      arguments: ["--path=/games/" + scummvmGameId, scummvmGameId],
      canvas: CANVAS_EL,
      print: function (text) {
        console.log("[scummvm]", text);
      },
      printErr: function (text) {
        console.error("[scummvm]", text);
      },
      preRun: [
        function () {
          try {
            mountGameTar(window.Module.FS, entries, scummvmGameId);
          } catch (err) {
            postError("failed to mount game tar: " + err.message);
            throw err;
          }

          // Save persistence (IDBFS at /home/web_user) is wired up
          // in #794 phase 3 — see the issue's "Save state model"
          // section. Phase 1 mounts /home/web_user as MEMFS so the
          // ScummVM launcher / config writes don't crash, but saves
          // disappear on reload. Acceptable trade-off for an
          // experiment that's primarily testing whether the engine
          // boots at all.
          try {
            const FS = window.Module.FS;
            FS.mkdirTree("/home/web_user");
          } catch (err) {
            console.warn("[scummvm-shell] /home/web_user mkdir failed:", err);
          }
        },
      ],
      onRuntimeInitialized: function () {
        // ScummVM's main() is invoked by Emscripten after preRun + the
        // wasm finishes instantiating. Once it starts, the canvas
        // takes over rendering.
        setStatus(null);
        postToParent({ type: "game-started" });
      },
      monitorRunDependencies: function (left) {
        if (left > 0) {
          setStatus("Loading ScummVM… (" + left + " deps)");
        }
      },
      setStatus: function (text) {
        if (text) setStatus(text);
      },
    };

    // Now load the ScummVM JS. It will pick up the global Module we
    // just configured and start running on its own.
    setStatus("Loading ScummVM runtime…");
    const script = document.createElement("script");
    script.src = "./scummvm.js";
    script.async = true;
    script.onerror = function () {
      postError("failed to load scummvm.js");
    };
    document.body.appendChild(script);
  }

  function handleMessage(event) {
    const data = event.data;
    if (!data || typeof data !== "object" || typeof data.type !== "string") {
      return;
    }
    switch (data.type) {
      case "init":
        handleInit(data);
        break;
      case "pause":
        // ScummVM doesn't expose a clean pause hook from outside;
        // best-effort: blur the canvas (its game loop continues but
        // input stops). Documenting this limitation in #794 phase 4.
        try {
          CANVAS_EL.blur();
        } catch (e) {}
        break;
      case "resume":
        try {
          CANVAS_EL.focus();
        } catch (e) {}
        break;
      case "request-save-state":
        // Save-state UX divergence is acknowledged in #794 phase 3 —
        // ScummVM saves are per-target files, not a single opaque blob.
        // Phase 1 ships without save-state plumbing; the host will see
        // the request silently no-op until phase 3 wires the flush-and-tar
        // path. Sending an empty response keeps the host's UI from hanging.
        postToParent({
          type: "save-state-error",
          error: "ScummVM save-state not implemented yet (#794 phase 3)",
        });
        break;
      case "load-save-state":
        postToParent({
          type: "save-state-error",
          error: "ScummVM load-save-state not implemented yet (#794 phase 3)",
        });
        break;
      case "update-preferences":
        // No ScummVM-equivalent preference plumbing yet.
        break;
      default:
        // Ignore unknown message types so future additions don't break us.
        break;
    }
  }

  window.addEventListener("message", handleMessage);

  // Tell the parent we're alive so it can fire init.
  postToParent({ type: "emulator-ready" });
})();
