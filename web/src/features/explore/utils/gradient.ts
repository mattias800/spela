export function buildGradient(colors: string[]): string {
  if (colors.length === 0) return "linear-gradient(135deg, #4a1a6b, #6b21a8)";
  if (colors.length === 1)
    return `linear-gradient(135deg, ${colors[0]}, ${colors[0]})`;
  return `linear-gradient(135deg, ${colors.join(", ")})`;
}
