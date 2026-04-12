import { useRef, useState, useEffect, useCallback } from "react";
import type { ReactNode } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { Section, TitledSection } from "@/components/ui";
import { GameCardSkeleton } from "@/components/ui";

interface ScrollShelfProps {
  title: string;
  subtitle?: string;
  icon?: LucideIcon;
  testId: string;
  isLoading: boolean;
  isEmpty: boolean;
  children: ReactNode;
  loadingSkeleton?: ReactNode;
  headerRight?: ReactNode;
}

export function ScrollShelf({
  title,
  subtitle,
  icon,
  testId,
  isLoading,
  isEmpty,
  children,
  loadingSkeleton,
  headerRight,
}: ScrollShelfProps) {
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
  }, [updateScrollState, children]);

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
    return (
      <Section data-testid={`${testId}-skeleton`} className="border-0 bg-white/[0.03] p-5">
        <div className="flex items-center gap-2 mb-1">
          {icon && <icon className="h-5 w-5 text-surface-400" />}
          <div className="h-7 w-60 rounded bg-surface-800 animate-pulse" />
        </div>
        {subtitle && <div className="h-4 w-40 rounded bg-surface-800/60 animate-pulse mt-1 mb-5" />}
        {loadingSkeleton ?? (
          <div className="flex gap-5 overflow-hidden mt-4">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="w-40 sm:w-44 lg:w-48 flex-shrink-0">
                <GameCardSkeleton />
              </div>
            ))}
          </div>
        )}
      </Section>
    );
  }

  if (isEmpty) return null;

  return (
    <Section data-testid={testId} data-comp="ScrollShelf" className="group/shelf relative border-0 bg-white/[0.03] p-5">
      <TitledSection title={title} icon={icon} renderRight={headerRight}>
        {subtitle && <p className="text-sm text-surface-400 -mt-3 mb-4">{subtitle}</p>}

        <div className="relative">
          {canScrollLeft && (
            <button
              onClick={() => scroll("left")}
              className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 transition-all duration-300 shadow-lg"
              aria-label={`Scroll ${title} left`}
            >
              <ChevronLeft className="h-5 w-5" />
            </button>
          )}
          {canScrollRight && (
            <button
              onClick={() => scroll("right")}
              className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/shelf:opacity-100 group-focus-within/shelf:opacity-100 focus-visible:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 transition-all duration-300 shadow-lg"
              aria-label={`Scroll ${title} right`}
            >
              <ChevronRight className="h-5 w-5" />
            </button>
          )}

          <div
            ref={scrollRef}
            className="flex gap-5 overflow-x-auto scrollbar-hide pb-2"
            style={{ scrollbarWidth: "none", msOverflowStyle: "none" }}
            role="list"
            aria-label={title}
          >
            {children}
          </div>
        </div>
      </TitledSection>
    </Section>
  );
}
