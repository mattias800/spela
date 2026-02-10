export type KeyMappingMode = "arrows-left" | "wasd-arrows" | "custom";

export const KEY_MAPPING_MODES: { value: KeyMappingMode; label: string }[] = [
  { value: "arrows-left", label: "Arrows + Left" },
  { value: "wasd-arrows", label: "WASD + Arrows" },
  { value: "custom", label: "Custom" },
];

// Button index → key name for each preset
export const PRESET_ARROWS_LEFT: Record<string, string> = {
  "0": "z", "1": "x", "2": "v", "3": "enter",
  "4": "arrowup", "5": "arrowdown", "6": "arrowleft", "7": "arrowright",
  "8": "a", "9": "s", "10": "q", "11": "e", "12": "r", "13": "f",
};

export const PRESET_WASD_ARROWS: Record<string, string> = {
  "0": "arrowdown", "1": "arrowright", "2": "v", "3": "enter",
  "4": "w", "5": "s", "6": "a", "7": "d",
  "8": "arrowleft", "9": "arrowup", "10": "q", "11": "]", "12": "e", "13": "[",
};

// Button metadata with position-based labels (NOT A/B/X/Y)
export const BUTTONS = [
  { index: "0", label: "Face Bottom", group: "face" },
  { index: "1", label: "Face Right", group: "face" },
  { index: "8", label: "Face Left", group: "face" },
  { index: "9", label: "Face Top", group: "face" },
  { index: "4", label: "D-pad Up", group: "dpad" },
  { index: "5", label: "D-pad Down", group: "dpad" },
  { index: "6", label: "D-pad Left", group: "dpad" },
  { index: "7", label: "D-pad Right", group: "dpad" },
  { index: "10", label: "L1", group: "shoulder" },
  { index: "11", label: "R1", group: "shoulder" },
  { index: "12", label: "L2", group: "shoulder" },
  { index: "13", label: "R2", group: "shoulder" },
  { index: "2", label: "Select", group: "meta" },
  { index: "3", label: "Start", group: "meta" },
] as const;

export function getPresetMapping(mode: KeyMappingMode): Record<string, string> {
  if (mode === "wasd-arrows") return PRESET_WASD_ARROWS;
  return PRESET_ARROWS_LEFT;
}

/** Format a key name for display (e.g., "arrowup" → "Arrow Up", "z" → "Z") */
export function formatKeyName(key: string): string {
  if (!key) return "—";
  const lower = key.toLowerCase();
  if (lower === "arrowup") return "↑";
  if (lower === "arrowdown") return "↓";
  if (lower === "arrowleft") return "←";
  if (lower === "arrowright") return "→";
  if (lower === "enter") return "Enter";
  if (lower === " " || lower === "space") return "Space";
  if (lower === "escape") return "Esc";
  if (lower === "backspace") return "Bksp";
  if (lower === "tab") return "Tab";
  return key.toUpperCase();
}
