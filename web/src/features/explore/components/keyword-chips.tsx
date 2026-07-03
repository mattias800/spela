import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight, Tag } from "lucide-react";
import { Skeleton } from "@/components/ui";
import type { Keyword } from "@/types/api";

interface KeywordChipsProps {
  keywords: Keyword[] | undefined;
  isLoading: boolean;
}

function KeywordChipsSkeleton() {
  const chipWidths = [96, 132, 108, 140, 88, 124, 116, 136, 100, 128, 92, 120];

  return (
    <section data-comp="KeywordChipsSkeleton" data-testid="keyword-chips-skeleton">
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        Popular Keywords
      </h2>
      <div className="flex gap-3 overflow-hidden flex-wrap">
        {chipWidths.map((width, i) => (
          <Skeleton
            key={i}
            className="h-9 rounded-full"
            style={{ width }}
          />
        ))}
      </div>
    </section>
  );
}

export function KeywordChips({ keywords, isLoading }: KeywordChipsProps) {
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
  }, [updateScrollState, keywords]);

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
    return <KeywordChipsSkeleton />;
  }

  if (!keywords || keywords.length === 0) {
    return null;
  }

  return (
    <section data-comp="KeywordChips" data-testid="keyword-chips" className="group/keywords relative">
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        Popular Keywords
      </h2>

      <div className="relative">
        {/* Scroll arrows */}
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/keywords:opacity-100 group-focus-within/keywords:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100 transition-all duration-300 shadow-lg"
            aria-label="Scroll keywords left"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/keywords:opacity-100 group-focus-within/keywords:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100 transition-all duration-300 shadow-lg"
            aria-label="Scroll keywords right"
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        )}

        {/* Scrollable row of chips */}
        <div
          ref={scrollRef}
          className="flex gap-3 overflow-x-auto scrollbar-hide pb-2"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
          role="list"
          aria-label="Popular Keywords"
        >
          {keywords.map((keyword) => (
            <Link
              key={keyword.id}
              to={`/explore/keywords/${keyword.id}`}
              role="listitem"
              className="flex-shrink-0 rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-surface-950"
            >
              <div className="flex items-center gap-2 px-4 py-2 rounded-full bg-surface-800/60 border border-surface-700/50 text-surface-200 hover:bg-surface-700/60 hover:border-surface-600/50 hover:text-surface-100 transition-all duration-200 group/chip">
                <Tag className="h-3.5 w-3.5 text-surface-400 group-hover/chip:text-brand-400 transition-colors" />
                <span className="text-sm font-medium whitespace-nowrap">
                  {keyword.name}
                </span>
                <span className="text-xs text-surface-500 tabular-nums">
                  {keyword.gameCount}
                </span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
