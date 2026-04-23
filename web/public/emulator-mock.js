/**
 * Mock EmulatorJS iframe used by Playwright tests.
 *
 * Speaks the same parent ↔ iframe postMessage protocol as
 * `/emulator.js`, but instead of actually emulating anything it
 * deterministically responds to commands so the React side can be
 * exercised end-to-end without EmulatorJS, WASM cores, BIOS files,
 * or real ROM data.
 *
 * Tests inspect what happened via `window.__spelaEmulatorMock__`,
 * which exposes:
 *
 *   inits         array of every `init` config received (in order)
 *   saveRequests  count of `request-save-state` commands received
 *   pauseCount    count of `pause` commands
 *   resumeCount   count of `resume` commands
 *   reset()       clear all of the above (between scenarios)
 *
 * The mock lives behind a `?mockEmulator=true` query string on the
 * play page — production builds never load it.
 */
(function () {
  "use strict";

  var parentOrigin = window.location.origin;

  // The iframe gets reloaded mid-flow during a disc switch (the parent
  // resets `iframe.src` to force a fresh boot), so per-iframe state is
  // ephemeral. Tests need a counter that survives that reload, so we
  // also push every event up to the parent window which lives across
  // reloads.
  function parentStore() {
    if (window.parent === window) return null;
    var p = window.parent;
    if (!p.__spelaEmulatorMockParent__) {
      p.__spelaEmulatorMockParent__ = {
        inits: [],
        saveRequests: 0,
        pauseCount: 0,
        resumeCount: 0,
        lastSaveStatePayload: null,
        reset: function () {
          var s = p.__spelaEmulatorMockParent__;
          s.inits.length = 0;
          s.saveRequests = 0;
          s.pauseCount = 0;
          s.resumeCount = 0;
          s.lastSaveStatePayload = null;
        },
      };
    }
    return p.__spelaEmulatorMockParent__;
  }

  var state = {
    inits: [],
    saveRequests: 0,
    pauseCount: 0,
    resumeCount: 0,
    lastSaveStatePayload: null,
    reset: function () {
      state.inits.length = 0;
      state.saveRequests = 0;
      state.pauseCount = 0;
      state.resumeCount = 0;
      state.lastSaveStatePayload = null;
      logEl.textContent = "";
      var ps = parentStore();
      if (ps) ps.reset();
    },
  };
  window.__spelaEmulatorMock__ = state;

  var logEl = document.getElementById("log");
  function log(msg) {
    if (!logEl) return;
    var line = "[" + new Date().toISOString().slice(11, 23) + "] " + msg + "\n";
    logEl.textContent += line;
  }

  function sendToParent(msg) {
    if (window.parent && window.parent !== window) {
      window.parent.postMessage(msg, parentOrigin);
    }
  }

  // Deterministic dummy save state — base64 of "MOCK_SAVE_STATE_V1".
  // Tests can match on this exact value to assert the save-state
  // round-trip carried the right bytes.
  var MOCK_SAVE_STATE_B64 = "TU9DS19TQVZFX1NUQVRFX1YxAA==";

  function summariseInit(config) {
    return JSON.stringify({
      core: config.core,
      gameName: config.gameName,
      romUrl: config.romUrl ? config.romUrl.slice(0, 60) : "",
      hasRomData: !!config.romData,
      targetDisc:
        typeof config.targetDisc === "number" ? config.targetDisc : null,
      hasSaveState: !!config.saveStateData,
      biosUrlCount: config.biosUrls ? config.biosUrls.length : 0,
    });
  }

  function handleInit(config) {
    // Snapshot the init so the test can assert on it. ArrayBuffer is
    // not structured-clone-friendly across iframe boundaries when we
    // want to inspect it in tests, so we record only its byte length.
    var snapshot = {
      core: config.core,
      gameName: config.gameName,
      romUrl: config.romUrl,
      romDataByteLength: config.romData
        ? config.romData.byteLength
        : null,
      targetDisc:
        typeof config.targetDisc === "number" ? config.targetDisc : null,
      saveStateData: config.saveStateData || null,
      biosUrls: config.biosUrls ? config.biosUrls.slice() : null,
      preferences: config.preferences,
    };
    state.inits.push(snapshot);
    var ps = parentStore();
    if (ps) ps.inits.push(snapshot);
    log("init " + summariseInit(config));

    // Mimic the real iframe's handshake — first say we're ready, then
    // a tick later say the game has started. The parent state machine
    // depends on both signals.
    setTimeout(function () {
      sendToParent({ type: "emulator-ready" });
    }, 0);
    setTimeout(function () {
      sendToParent({ type: "game-started" });
    }, 10);
  }

  function handleRequestSaveState() {
    state.saveRequests++;
    var ps = parentStore();
    if (ps) ps.saveRequests++;
    log("request-save-state #" + state.saveRequests);
    setTimeout(function () {
      state.lastSaveStatePayload = MOCK_SAVE_STATE_B64;
      if (ps) ps.lastSaveStatePayload = MOCK_SAVE_STATE_B64;
      sendToParent({
        type: "save-state-response",
        data: MOCK_SAVE_STATE_B64,
        screenshot: "",
      });
    }, 0);
  }

  window.addEventListener("message", function (event) {
    if (event.origin !== parentOrigin) return;
    var msg = event.data;
    if (!msg || typeof msg.type !== "string") return;

    switch (msg.type) {
      case "init":
        handleInit(msg);
        break;
      case "request-save-state":
        handleRequestSaveState();
        break;
      case "load-save-state":
        log("load-save-state name=" + (msg.name || ""));
        break;
      case "pause":
        state.pauseCount++;
        {
          var ps1 = parentStore();
          if (ps1) ps1.pauseCount++;
        }
        log("pause #" + state.pauseCount);
        break;
      case "resume":
        state.resumeCount++;
        {
          var ps2 = parentStore();
          if (ps2) ps2.resumeCount++;
        }
        log("resume #" + state.resumeCount);
        break;
      case "update-preferences":
        log("update-preferences");
        break;
    }
  });

  log("mock ready, awaiting init");
})();
