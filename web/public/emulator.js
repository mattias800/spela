/**
 * Spela EmulatorJS iframe host.
 *
 * Receives commands from parent via postMessage, configures EmulatorJS
 * globals, loads the emulator, and relays lifecycle events back.
 */
(function () {
  "use strict";

  // Ensure WebGL contexts preserve their drawing buffer so that
  // canvas.toDataURL() returns actual frame content for save-state
  // screenshots instead of a cleared (transparent) buffer.
  var _origGetContext = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (type, attrs) {
    if (
      type === "webgl" ||
      type === "webgl2" ||
      type === "experimental-webgl"
    ) {
      attrs = Object.assign({}, attrs || {}, {
        preserveDrawingBuffer: true,
      });
    }
    return _origGetContext.call(this, type, attrs);
  };

  var parentOrigin = window.location.origin;

  // Catch uncaught errors/exceptions and forward to parent as emulator-error.
  // This catches WASM core crashes, OOM, and other fatal errors.
  window.addEventListener("error", function (event) {
    sendToParent({
      type: "emulator-error",
      error: event.message || "An unexpected error occurred in the emulator",
    });
  });
  window.addEventListener("unhandledrejection", function (event) {
    var reason =
      event.reason instanceof Error
        ? event.reason.message
        : String(event.reason || "Emulator promise rejected");
    sendToParent({
      type: "emulator-error",
      error: reason,
    });
  });

  /** Send a typed message to the parent window. */
  function sendToParent(msg) {
    if (window.parent && window.parent !== window) {
      window.parent.postMessage(msg, parentOrigin);
    }
  }

  /** Convert a base64 string to a Uint8Array. */
  function base64ToUint8Array(b64) {
    var binary = atob(b64);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }

  /** Convert a Uint8Array to a base64 string. */
  function uint8ArrayToBase64(bytes) {
    var binary = "";
    for (var i = 0; i < bytes.length; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  var initialized = false;

  window.addEventListener("message", function (event) {
    if (event.origin !== parentOrigin) return;
    var msg = event.data;
    if (!msg || typeof msg.type !== "string") return;

    switch (msg.type) {
      case "init":
        if (!initialized) {
          initialized = true;
          initEmulator(msg);
        }
        break;

      case "request-save-state":
        handleRequestSaveState();
        break;

      case "load-save-state":
        handleLoadSaveState(msg.data, msg.name);
        break;

      case "pause":
        if (window.EJS_emulator) {
          window.EJS_emulator.pause();
        }
        break;

      case "resume":
        if (window.EJS_emulator) {
          window.EJS_emulator.play();
        }
        break;

      case "update-preferences":
        // Preferences are applied at init time via EJS options.
        // Runtime preference changes would require reloading.
        break;
    }
  });

  function initEmulator(config) {
    // Use pre-loaded data if provided (disc switch flow)
    if (config.romData) {
      var blob = new Blob([config.romData], { type: "application/zip" });
      window.EJS_gameUrl = URL.createObjectURL(blob);
    } else {
      window.EJS_gameUrl = config.romUrl;
    }

    // Set EmulatorJS configuration globals
    window.EJS_player = "#game";
    window.EJS_core = config.core;
    window.EJS_gameName = config.gameName;
    window.EJS_color = "#00a3e0"; // brand-cyan
    window.EJS_startOnLoaded = true;
    window.EJS_backgroundBlur = true;
    window.EJS_backgroundColor = "#16191d";

    // Skip loading emulator.min.js/css — the npm package has no minified
    // builds, and the SPA fallback serves index.html (200 text/html) for
    // missing files, which prevents the loader's onerror fallback from firing.
    // This also triggers a CDN version check that is harmlessly blocked by CSP.
    window.EJS_DEBUG_XX = true;

    // Enable threaded WASM cores when SharedArrayBuffer is available.
    // Requires COOP/COEP headers; falls back to non-threaded cores otherwise.
    window.EJS_threads = typeof SharedArrayBuffer === "function";

    // Auto-load save state if provided
    if (config.saveStateData) {
      var saveBytes = base64ToUint8Array(config.saveStateData);
      var saveBlob = new Blob([saveBytes]);
      window.EJS_loadStateURL = URL.createObjectURL(saveBlob);
    }

    // Set BIOS URL for systems that require it (e.g. PlayStation, GBA)
    if (config.biosUrls && config.biosUrls.length > 0) {
      window.EJS_biosUrl = config.biosUrls[0];
    }

    // Apply preferences
    var prefs = config.preferences || {};
    window.EJS_defaultOptions = window.EJS_defaultOptions || {};

    // Shader
    if (prefs.shader && prefs.shader !== "none" && prefs.shader !== "") {
      window.EJS_defaultOptions["shader"] = prefs.shader;
    }

    // Performance overlay (FPS counter)
    if (prefs.showPerformanceOverlay) {
      window.EJS_defaultOptions["fpsCounter"] = "enabled";
    }

    // Volume
    if (typeof prefs.volume === "number") {
      window.EJS_volume = prefs.volume;
    }

    // Key mapping
    // EmulatorJS uses its own key name format (e.g., "up arrow" not "arrowup").
    // This map converts standard DOM KeyboardEvent.key names to EmulatorJS names.
    var ejsKeyMap = {
      arrowup: "up arrow",
      arrowdown: "down arrow",
      arrowleft: "left arrow",
      arrowright: "right arrow",
    };
    function toEjsKey(key) {
      var lower = (key || "").toLowerCase();
      return ejsKeyMap[lower] || lower;
    }

    var keyPrefs = prefs.keyMapping || "arrows-left";
    var keyPresets = {
      "arrows-left": {
        0: { value: "z" },
        1: { value: "x" },
        2: { value: "v" },
        3: { value: "enter" },
        4: { value: "up arrow" },
        5: { value: "down arrow" },
        6: { value: "left arrow" },
        7: { value: "right arrow" },
        8: { value: "a" },
        9: { value: "s" },
        10: { value: "q" },
        11: { value: "e" },
        12: { value: "r" },
        13: { value: "f" },
      },
      "wasd-arrows": {
        0: { value: "down arrow" },
        1: { value: "right arrow" },
        2: { value: "v" },
        3: { value: "enter" },
        4: { value: "w" },
        5: { value: "s" },
        6: { value: "a" },
        7: { value: "d" },
        8: { value: "left arrow" },
        9: { value: "up arrow" },
        10: { value: "q" },
        11: { value: "]" },
        12: { value: "e" },
        13: { value: "[" },
      },
    };

    var controls = {};
    if (keyPrefs === "custom" && prefs.customKeyMapping) {
      for (var idx in prefs.customKeyMapping) {
        controls[parseInt(idx)] = {
          value: toEjsKey(prefs.customKeyMapping[idx]),
          value2: "",
        };
      }
    } else {
      var preset = keyPresets[keyPrefs] || keyPresets["arrows-left"];
      for (var idx in preset) {
        controls[parseInt(idx)] = {
          value: preset[idx].value,
          value2: "",
        };
      }
    }
    window.EJS_defaultControls = {
      0: controls,
      1: controls,
      2: controls,
      3: controls,
    };

    // Hide buttons we don't need in embedded context
    window.EJS_Buttons = {
      exitEmulation: false,
    };

    // Callbacks
    window.EJS_ready = function () {
      sendToParent({ type: "emulator-ready" });
    };

    var pendingDiscSwitch = (typeof config.targetDisc === "number") ? config.targetDisc : null;

    window.EJS_onGameStart = function () {
      sendToParent({ type: "game-started" });
      if (pendingDiscSwitch !== null) {
        setTimeout(function () {
          if (window.EJS_emulator && window.EJS_emulator.gameManager) {
            window.EJS_emulator.gameManager.setCurrentDisk(pendingDiscSwitch);
          }
          pendingDiscSwitch = null;
        }, 500);
      }
    };

    window.EJS_onSaveState = function () {
      // Triggered when user clicks save in EmulatorJS UI
      handleRequestSaveState();
    };

    window.EJS_onLoadState = function () {
      // Triggered when user clicks load in EmulatorJS UI
      // Parent will handle showing the save picker
      sendToParent({
        type: "save-state-response",
        data: "",
        screenshot: "",
      });
    };

    window.EJS_onSaveUpdate = function () {
      // SRAM/battery save data changed
      try {
        var emu = window.EJS_emulator;
        if (!emu || !emu.gameManager) return;
        var saveFile = emu.gameManager.getSaveFile();
        if (saveFile && saveFile.length > 0) {
          sendToParent({
            type: "sram-update",
            data: uint8ArrayToBase64(saveFile),
          });
        }
      } catch (_) {
        // Ignore SRAM capture errors
      }
    };

    // Tell EmulatorJS where to find its runtime assets (cores, shaders, etc.)
    window.EJS_pathtodata = "/emulatorjs/data/";

    // Load EmulatorJS script
    var script = document.createElement("script");
    script.src = "/emulatorjs/data/loader.js";
    script.onerror = function () {
      sendToParent({
        type: "emulator-error",
        error:
          "Failed to load EmulatorJS. Check that emulator assets are installed.",
      });
    };
    document.body.appendChild(script);
  }

  function handleRequestSaveState() {
    try {
      var emu = window.EJS_emulator;
      if (!emu || !emu.gameManager) {
        sendToParent({
          type: "save-state-error",
          error: "Emulator not ready",
        });
        return;
      }

      var stateData = emu.gameManager.getState();
      if (!stateData || stateData.length === 0) {
        sendToParent({
          type: "save-state-error",
          error: "Failed to capture save state",
        });
        return;
      }

      var b64 = uint8ArrayToBase64(stateData);

      // Try to capture a screenshot
      var screenshot = "";
      try {
        var canvas = document.querySelector("#game canvas");
        if (canvas) {
          screenshot = canvas.toDataURL("image/png");
        }
      } catch (_) {
        // Screenshot capture may fail due to tainted canvas
      }

      sendToParent({
        type: "save-state-response",
        data: b64,
        screenshot: screenshot,
      });
    } catch (err) {
      sendToParent({
        type: "save-state-error",
        error: err.message || "Unknown save error",
      });
    }
  }

  function handleLoadSaveState(b64Data, name) {
    try {
      var emu = window.EJS_emulator;
      if (!emu || !emu.gameManager) return;

      var bytes = base64ToUint8Array(b64Data);
      emu.gameManager.loadState(bytes);
    } catch (err) {
      sendToParent({
        type: "save-state-error",
        error:
          "Failed to load save state: " +
          (err.message || "Unknown error"),
      });
    }
  }
})();
