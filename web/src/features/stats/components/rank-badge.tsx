import { cn } from "@/lib/cn";

function rankStyle(rank: number): { text: string; bg: string } {
  if (rank === 1)
    return {
      text: "text-amber-400",
      bg: "bg-amber-400/15 border border-amber-400/30",
    };
  if (rank === 2)
    return {
      text: "text-surface-300",
      bg: "bg-surface-300/15 border border-surface-300/30",
    };
  if (rank === 3)
    return {
      text: "text-amber-700",
      bg: "bg-amber-700/15 border border-amber-700/30",
    };
  return { text: "text-surface-500", bg: "" };
}

// Leaderboard rank pill — gold/silver/bronze for the top 3, plain otherwise.
export function RankBadge({ rank }: { rank: number }) {
  const style = rankStyle(rank);
  const isTop3 = rank <= 3;

  if (isTop3) {
    return (
      <span
        data-comp="RankBadge"
        className={cn(
          "inline-flex items-center justify-center h-7 w-7 rounded-full text-xs font-bold",
          style.bg,
          style.text,
        )}
      >
        {rank}
      </span>
    );
  }

  return (
    <span
      data-comp="RankBadge"
      className="inline-flex items-center justify-center h-7 w-7 text-sm font-medium text-surface-500"
    >
      {rank}
    </span>
  );
}
