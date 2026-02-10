/**
 * PostMessage protocol between the parent React app and the EmulatorJS iframe.
 *
 * Parent → Iframe messages: commands to initialize, save, load, configure.
 * Iframe → Parent messages: lifecycle events and data responses.
 */

// ── Parent → Iframe ──────────────────────────────────────────────────

export interface InitEmulatorMessage {
  type: "init";
  romUrl: string;
  core: string;
  gameName: string;
  saveStateData?: string; // base64-encoded save state to auto-load
  preferences: EmulatorPreferences;
}

export interface RequestSaveStateMessage {
  type: "request-save-state";
}

export interface LoadSaveStateMessage {
  type: "load-save-state";
  data: string; // base64-encoded save state
  name: string;
}

export interface UpdatePreferencesMessage {
  type: "update-preferences";
  preferences: EmulatorPreferences;
}

export interface PauseMessage {
  type: "pause";
}

export interface ResumeMessage {
  type: "resume";
}

export type ParentToIframeMessage =
  | InitEmulatorMessage
  | RequestSaveStateMessage
  | LoadSaveStateMessage
  | UpdatePreferencesMessage
  | PauseMessage
  | ResumeMessage;

// ── Iframe → Parent ──────────────────────────────────────────────────

export interface EmulatorReadyEvent {
  type: "emulator-ready";
}

export interface GameStartedEvent {
  type: "game-started";
}

export interface SaveStateResponseEvent {
  type: "save-state-response";
  data: string; // base64-encoded save state
  screenshot?: string; // base64 PNG screenshot
}

export interface SaveStateErrorEvent {
  type: "save-state-error";
  error: string;
}

export interface EmulatorErrorEvent {
  type: "emulator-error";
  error: string;
}

export interface RomLoadProgressEvent {
  type: "rom-load-progress";
  loaded: number;
  total: number;
}

export interface SramUpdateEvent {
  type: "sram-update";
  data: string; // base64-encoded SRAM/battery save data
}

export type IframeToParentMessage =
  | EmulatorReadyEvent
  | GameStartedEvent
  | SaveStateResponseEvent
  | SaveStateErrorEvent
  | EmulatorErrorEvent
  | RomLoadProgressEvent
  | SramUpdateEvent;

// ── Shared Types ─────────────────────────────────────────────────────

export interface EmulatorPreferences {
  shader: string;
  showPerformanceOverlay: boolean;
  volume?: number;
}

/** Discriminated union of all messages for type-safe handling. */
export type EmulatorMessage = ParentToIframeMessage | IframeToParentMessage;

/** Type guard for iframe → parent messages. */
export function isIframeMessage(msg: unknown): msg is IframeToParentMessage {
  if (typeof msg !== "object" || msg === null || !("type" in msg)) return false;
  const type = (msg as { type: string }).type;
  return [
    "emulator-ready",
    "game-started",
    "save-state-response",
    "save-state-error",
    "emulator-error",
    "rom-load-progress",
    "sram-update",
  ].includes(type);
}

/** Type guard for parent → iframe messages. */
export function isParentMessage(msg: unknown): msg is ParentToIframeMessage {
  if (typeof msg !== "object" || msg === null || !("type" in msg)) return false;
  const type = (msg as { type: string }).type;
  return [
    "init",
    "request-save-state",
    "load-save-state",
    "update-preferences",
    "pause",
    "resume",
  ].includes(type);
}
