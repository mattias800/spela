import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Skeleton } from "@/components/ui";
import type { ConsoleHighlight } from "@/types/api";

interface ConsoleQuickJumpProps {
  consoles: ConsoleHighlight[] | undefined;
  isLoading: boolean;
}

function ConsoleQuickJumpSkeleton() {
  return (
    <section data-comp="ConsoleQuickJumpSkeleton" data-testid="console-quick-jump-skeleton">
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        Browse by Console
      </h2>
      <div className="flex gap-4 overflow-hidden">
        {Array.from({ length: 8 }, (_, i) => (
          <Skeleton key={i} className="w-36 h-24 rounded-xl flex-shrink-0" />
        ))}
      </div>
    </section>
  );
}

export function ConsoleQuickJump({
  consoles,
  isLoading,
}: ConsoleQuickJumpProps) {
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
  }, [updateScrollState, consoles]);

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
    return <ConsoleQuickJumpSkeleton />;
  }

  if (!consoles || consoles.length === 0) {
    return null;
  }

  return (
    <section data-comp="ConsoleQuickJump"
      data-testid="console-quick-jump"
      className="group/console-jump relative"
    >
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        Browse by Console
      </h2>

      <div className="relative">
        {/* Scroll arrows */}
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/console-jump:opacity-100 group-focus-within/console-jump:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label="Scroll consoles left"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/console-jump:opacity-100 group-focus-within/console-jump:opacity-100 transition-all duration-300 shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:opacity-100"
            aria-label="Scroll consoles right"
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        )}

        <div
          ref={scrollRef}
          className="flex gap-4 overflow-x-auto scrollbar-hide pb-2"
          style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
          role="list"
          aria-label="Browse by Console"
        >
          {consoles.map((con) => {
            const color = con.colorTheme || "#6366f1";
            return (
              <Link
                key={con.id}
                to={`/consoles/${con.id}`}
                className="group/card flex-shrink-0 w-36 rounded-xl border border-white/[0.06] p-4 transition-all duration-200 hover:border-white/[0.12] hover:-translate-y-0.5 hover:shadow-lg"
                style={{
                  background: `linear-gradient(135deg, ${color}30, ${color}08)`,
                }}
                role="listitem"
                data-testid={`console-card-${con.id}`}
              >
                {con.iconUrl && (
                  <img
                    src={con.iconUrl}
                    alt=""
                    className="h-8 w-8 object-contain mb-2"
                    style={{ imageRendering: "pixelated" }}
                  />
                )}
                <p className="text-sm font-semibold text-surface-100 truncate group-hover/card:text-brand-400 transition-colors">
                  {con.name}
                </p>
                <p className="text-xs text-surface-400 mt-0.5">
                  {con.gameCount} {con.gameCount === 1 ? "game" : "games"}
                </p>
              </Link>
            );
          })}
        </div>
      </div>
    </section>
  );
}
