export function rankColor(rank: number): string {
  if (rank === 1) return "text-amber-400";
  if (rank === 2) return "text-surface-300";
  if (rank === 3) return "text-amber-700";
  return "text-surface-500";
}
