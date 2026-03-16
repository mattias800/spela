/** Ensures a hex color has enough brightness for dark backgrounds.
 *  Lightens colors below a perceived brightness threshold. */
export function ensureContrast(hex: string): string {
  if (!hex || hex.length < 7) return hex;
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  if (isNaN(r) || isNaN(g) || isNaN(b)) return hex;
  // Perceived brightness (ITU-R BT.709)
  const brightness = r * 0.299 + g * 0.587 + b * 0.114;
  if (brightness >= 100) return hex;
  // Lighten by scaling toward white
  const factor = 100 / Math.max(brightness, 10);
  const lr = Math.min(255, Math.round(r * factor));
  const lg = Math.min(255, Math.round(g * factor));
  const lb = Math.min(255, Math.round(b * factor));
  return `#${lr.toString(16).padStart(2, "0")}${lg.toString(16).padStart(2, "0")}${lb.toString(16).padStart(2, "0")}`;
}
