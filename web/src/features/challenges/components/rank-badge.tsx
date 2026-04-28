import { cn } from "@/lib/cn";

interface RankBadgeProps {
  rank: number;
}

function rankStyle(rank: number): { text: string; bg: string } {
  if (rank === 1)
    return {
      text: "text-gold-500",
      bg: "bg-gold-500/15 border border-gold-500/30",
    };
  if (rank === 2)
    return {
      text: "text-silver-500",
      bg: "bg-silver-500/15 border border-silver-500/30",
    };
  if (rank === 3)
    return {
      text: "text-bronze-500",
      bg: "bg-bronze-500/15 border border-bronze-500/30",
    };
  return { text: "text-surface-500", bg: "" };
}

export function RankBadge({ rank }: RankBadgeProps) {
  const style = rankStyle(rank);
  const isTop3 = rank <= 3;

  if (isTop3) {
    return (
      <span
        data-testid={`rank-badge-${rank}`}
        className={cn(
          "inline-flex items-center justify-center h-7 w-7 rounded-full text-xs font-bold flex-shrink-0",
          style.bg,
          style.text,
        )}
      >
        {rank}
      </span>
    );
  }

  return (
    <span data-comp="RankBadge"
      data-testid={`rank-badge-${rank}`}
      className="inline-flex items-center justify-center h-7 w-7 text-sm font-medium text-surface-500 flex-shrink-0"
    >
      {rank}
    </span>
  );
}
