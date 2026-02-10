/**
 * Maps Spela shader preset names to EmulatorJS shader identifiers.
 *
 * Spela uses custom WebGL2 shaders for the preview widget (shader-sources.ts),
 * but the actual emulator uses EmulatorJS's built-in RetroArch shaders.
 * This mapping bridges the two naming systems.
 */

const SPELA_TO_EMULATORJS: Record<string, string> = {
  "none": "",
  "bilinear": "",
  "sharp-bilinear": "",
  "scanlines": "crt-mattias",
  "crt-simple": "crt-easymode",
  "lcd-grid": "crt-aperture",
};

/**
 * Convert a Spela shader preset name to the corresponding EmulatorJS shader identifier.
 * Returns empty string (disabled) for unknown or filter-only presets.
 */
export function toEmulatorJsShader(spelaShader: string): string {
  return SPELA_TO_EMULATORJS[spelaShader] ?? "";
}
