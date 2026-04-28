import { Link } from "react-router-dom";
import { Skeleton } from "@/components/ui";
import { ScrollShelf } from "@/components/scroll-shelf";
import type { FeaturedSeries } from "@/types/api";

interface SeriesShelfProps {
  series: FeaturedSeries[] | undefined;
  isLoading: boolean;
}

function SeriesSkeletonContent() {
  return (
    <div data-comp="SeriesSkeletonContent" className="flex gap-4 overflow-hidden">
      {Array.from({ length: 5 }, (_, i) => (
        <Skeleton key={i} className="w-56 sm:w-60 lg:w-64 h-36 flex-shrink-0 rounded-2xl" />
      ))}
    </div>
  );
}

export function SeriesShelf({ series, isLoading }: SeriesShelfProps) {
  return (
    <ScrollShelf
      title="Browse by Series"
      testId="series-shelf"
      isLoading={isLoading}
      isEmpty={!series || series.length === 0}
      loadingSkeleton={<SeriesSkeletonContent />}
    >
      {series?.map((s) => (
        <Link
          key={s.id}
          to={`/explore/series/${s.id}`}
          className="w-56 sm:w-60 lg:w-64 flex-shrink-0 rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
          role="listitem"
        >
          <div className="relative h-36 rounded-2xl overflow-hidden border border-white/[0.06] transition-all duration-300 hover:border-white/[0.12] hover:shadow-xl hover:shadow-black/30 hover:-translate-y-1 group/card">
            {s.heroUrl ? (
              <img
                src={s.heroUrl}
                alt=""
                className="absolute inset-0 w-full h-full object-cover transition-transform duration-500 group-hover/card:scale-105"
                loading="lazy"
              />
            ) : (
              <div className="absolute inset-0 bg-gradient-to-br from-brand-900/80 via-brand-800/60 to-surface-950/80" />
            )}
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent pointer-events-none" />
            <div className="relative flex flex-col justify-end h-full p-4">
              <h3 className="text-base font-bold text-white leading-tight group-hover/card:text-brand-300 transition-colors truncate">
                {s.name}
              </h3>
              <p className="text-xs text-white/70 mt-1">
                {s.libraryGames}/{s.totalGames} {s.totalGames === 1 ? "game" : "games"}
                {s.consoleCount > 0 && (
                  <span className="text-white/50">
                    {" "}across {s.consoleCount} {s.consoleCount === 1 ? "console" : "consoles"}
                  </span>
                )}
              </p>
            </div>
          </div>
        </Link>
      ))}
    </ScrollShelf>
  );
}
