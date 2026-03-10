import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Skeleton } from "@/components/ui";
import type { FeaturedSeries } from "@/types/api";

interface SeriesShelfProps {
  series: FeaturedSeries[] | undefined;
  isLoading: boolean;
}

function SeriesShelfSkeleton() {
  return (
    <section data-testid="series-shelf-skeleton">
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        Browse by Series
      </h2>
      <div className="flex gap-4 overflow-hidden">
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton
            key={i}
            className="w-56 sm:w-60 lg:w-64 h-36 flex-shrink-0 rounded-2xl"
          />
        ))}
      </div>
    </section>
  );
}

export function SeriesShelf({ series, isLoading }: SeriesShelfProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateScrollState = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 0);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 1);
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    updateScrollState();
    el.addEventListener("scroll", updateScrollState, { passive: true });
    window.addEventListener("resize", updateScrollState);
    return () => {
      el.removeEventListener("scroll", updateScrollState);
      window.removeEventListener("resize", updateScrollState);
    };
  }, [updateScrollState, series]);

  const scroll = useCallback((direction: "left" | "right") => {
    const el = scrollRef.current;
    if (!el) return;
    const scrollAmount = el.clientWidth * 0.7;
    el.scrollBy({
      left: direction === "left" ? -scrollAmount : scrollAmount,
      behavior: "smooth",
    });
  }, []);

  if (isLoading) {
    return <SeriesShelfSkeleton />;
  }

  if (!series || series.length === 0) {
    return null;
  }

  return (
    <section data-testid="series-shelf" className="group/series relative">
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        Browse by Series
      </h2>

      <div className="relative">
        {/* Scroll arrows */}
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/series:opacity-100 group-focus-within/series:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100 transition-all duration-300 shadow-lg"
            aria-label="Scroll series left"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/series:opacity-100 group-focus-within/series:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100 transition-all duration-300 shadow-lg"
            aria-label="Scroll series right"
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        )}

        {/* Scrollable row */}
        <div
          ref={scrollRef}
          className="flex gap-4 overflow-x-auto scrollbar-hide pb-2"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
          role="list"
          aria-label="Browse by Series"
        >
          {series.map((s) => (
            <Link
              key={s.id}
              to={`/explore/series/${s.id}`}
              className="w-56 sm:w-60 lg:w-64 flex-shrink-0 rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
              role="listitem"
            >
              <div className="relative h-36 rounded-2xl overflow-hidden border border-white/[0.06] transition-all duration-300 hover:border-white/[0.12] hover:shadow-xl hover:shadow-black/30 hover:-translate-y-1 group/card">
                {/* Background image */}
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

                {/* Gradient overlay */}
                <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/40 to-transparent pointer-events-none" />

                {/* Content */}
                <div className="relative flex flex-col justify-end h-full p-4">
                  <h3 className="text-base font-bold text-white leading-tight group-hover/card:text-brand-300 transition-colors truncate">
                    {s.name}
                  </h3>
                  <p className="text-xs text-white/70 mt-1">
                    {s.libraryGames}/{s.totalGames} {s.totalGames === 1 ? "game" : "games"}
                    {s.consoleCount > 0 && (
                      <span className="text-white/50">
                        {" "}
                        across {s.consoleCount}{" "}
                        {s.consoleCount === 1 ? "console" : "consoles"}
                      </span>
                    )}
                  </p>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
