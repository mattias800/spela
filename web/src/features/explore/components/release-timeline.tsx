import { useState } from "react";
import { Link } from "react-router-dom";
import type { TimelineEntry } from "@/types/api";

interface ReleaseTimelineProps {
  timeline: TimelineEntry[];
}

export function ReleaseTimeline({ timeline }: ReleaseTimelineProps) {
  if (!timeline || timeline.length === 0) return null;

  // Sort entries by year ascending
  const sorted = [...timeline].sort((a, b) => a.year - b.year);

  return (
    <section data-testid="release-timeline">
      <h2 className="text-lg font-bold text-surface-100 mb-4">
        Release Timeline
      </h2>
      <div
        className="flex gap-6 overflow-x-auto pb-3 scrollbar-hide"
        style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
        data-testid="timeline-strip"
      >
        {sorted.map((entry) => (
          <TimelineYearColumn key={entry.year} entry={entry} />
        ))}
      </div>
    </section>
  );
}

function TimelineYearColumn({ entry }: { entry: TimelineEntry }) {
  return (
    <div className="flex-shrink-0 min-w-[100px]" data-testid={`timeline-year-${entry.year}`}>
      <p className="text-sm font-bold text-surface-200 mb-2">{entry.year}</p>
      <div className="flex flex-wrap gap-1.5" style={{ maxWidth: "120px" }}>
        {entry.games?.map((game) => (
          <TimelineCover key={game.id} game={game} />
        ))}
      </div>
    </div>
  );
}

function TimelineCover({
  game,
}: {
  game: NonNullable<TimelineEntry["games"]>[number];
}) {
  const [showTooltip, setShowTooltip] = useState(false);

  return (
    <div
      className="relative"
      onMouseEnter={() => setShowTooltip(true)}
      onMouseLeave={() => setShowTooltip(false)}
    >
      <Link to={`/games/${game.id}`}>
        <img
          src={game.coverUrl}
          alt={game.title}
          className="w-10 h-14 rounded object-cover border border-white/[0.06] hover:border-brand-500/50 transition-colors"
          loading="lazy"
          data-testid={`timeline-game-${game.id}`}
        />
      </Link>
      {showTooltip && (
        <div
          className="absolute z-20 bottom-full left-1/2 -translate-x-1/2 mb-2 px-2.5 py-1.5 rounded-lg bg-surface-800 border border-white/[0.08] shadow-lg whitespace-nowrap pointer-events-none"
          data-testid={`timeline-tooltip-${game.id}`}
        >
          <p className="text-xs font-medium text-surface-100">{game.title}</p>
          {game.igdbCriticsRating > 0 && (
            <p className="text-xs text-amber-400">{game.igdbCriticsRating.toFixed(1)}</p>
          )}
        </div>
      )}
    </div>
  );
}
