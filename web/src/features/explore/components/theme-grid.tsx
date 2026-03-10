import { useRef, useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Skeleton } from "@/components/ui";
import type { Theme } from "@/types/api";

// Visual gradients for theme cards — mapped by theme name patterns
const THEME_GRADIENTS: Record<string, string> = {
  "science fiction": "from-cyan-900/80 via-blue-900/60 to-indigo-950/80",
  "sci-fi": "from-cyan-900/80 via-blue-900/60 to-indigo-950/80",
  horror: "from-red-950/80 via-rose-900/60 to-gray-950/80",
  fantasy: "from-amber-900/80 via-orange-800/60 to-yellow-950/80",
  thriller: "from-slate-900/80 via-gray-800/60 to-zinc-950/80",
  comedy: "from-yellow-800/80 via-amber-700/60 to-orange-900/80",
  action: "from-red-900/80 via-orange-900/60 to-amber-950/80",
  drama: "from-purple-900/80 via-violet-800/60 to-indigo-950/80",
  mystery: "from-indigo-950/80 via-purple-900/60 to-slate-900/80",
  survival: "from-green-950/80 via-emerald-900/60 to-teal-950/80",
  stealth: "from-gray-900/80 via-slate-800/60 to-zinc-950/80",
  historical: "from-amber-950/80 via-yellow-900/60 to-orange-950/80",
  warfare: "from-stone-900/80 via-neutral-800/60 to-gray-950/80",
  romance: "from-pink-900/80 via-rose-800/60 to-fuchsia-950/80",
  sandbox: "from-lime-900/80 via-green-800/60 to-emerald-950/80",
  "open world": "from-sky-900/80 via-blue-800/60 to-cyan-950/80",
  educational: "from-teal-900/80 via-cyan-800/60 to-blue-950/80",
  "party game": "from-fuchsia-900/80 via-pink-800/60 to-rose-950/80",
  erotic: "from-rose-950/80 via-pink-900/60 to-red-950/80",
  business: "from-emerald-900/80 via-teal-800/60 to-cyan-950/80",
  "4x (explore, expand, exploit, exterminate)":
    "from-violet-900/80 via-indigo-800/60 to-blue-950/80",
  "non-fiction": "from-stone-800/80 via-neutral-700/60 to-gray-900/80",
};

const FALLBACK_GRADIENTS = [
  "from-violet-900/80 via-purple-800/60 to-indigo-950/80",
  "from-teal-900/80 via-emerald-800/60 to-green-950/80",
  "from-rose-900/80 via-pink-800/60 to-fuchsia-950/80",
  "from-sky-900/80 via-blue-800/60 to-cyan-950/80",
  "from-amber-900/80 via-yellow-800/60 to-orange-950/80",
  "from-slate-800/80 via-gray-700/60 to-zinc-900/80",
];

function getGradient(themeName: string, index: number): string {
  const lower = themeName.toLowerCase();
  for (const [key, gradient] of Object.entries(THEME_GRADIENTS)) {
    if (lower.includes(key) || key.includes(lower)) {
      return gradient;
    }
  }
  return FALLBACK_GRADIENTS[index % FALLBACK_GRADIENTS.length];
}

interface ThemeGridProps {
  themes: Theme[] | undefined;
  isLoading: boolean;
}

function ThemeGridSkeleton() {
  return (
    <section data-testid="theme-grid-skeleton">
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        Browse by Theme
      </h2>
      <div className="flex gap-4 overflow-hidden">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton
            key={i}
            className="w-44 sm:w-48 lg:w-52 h-28 flex-shrink-0 rounded-2xl"
          />
        ))}
      </div>
    </section>
  );
}

export function ThemeGrid({ themes, isLoading }: ThemeGridProps) {
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
  }, [updateScrollState, themes]);

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
    return <ThemeGridSkeleton />;
  }

  if (!themes || themes.length === 0) {
    return null;
  }

  return (
    <section data-testid="theme-grid" className="group/themes relative">
      <h2 className="text-xl font-bold text-surface-100 mb-5">
        Browse by Theme
      </h2>

      <div className="relative">
        {/* Scroll arrows */}
        {canScrollLeft && (
          <button
            onClick={() => scroll("left")}
            className="absolute -left-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/themes:opacity-100 transition-all duration-300 shadow-lg"
            aria-label="Scroll themes left"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>
        )}
        {canScrollRight && (
          <button
            onClick={() => scroll("right")}
            className="absolute -right-2 top-1/2 -translate-y-1/2 z-10 p-2 rounded-full bg-surface-900/90 text-surface-300 hover:text-surface-100 hover:bg-surface-800 opacity-0 group-hover/themes:opacity-100 transition-all duration-300 shadow-lg"
            aria-label="Scroll themes right"
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
          aria-label="Browse by Theme"
        >
          {themes.map((theme, index) => (
            <Link
              key={theme.id}
              to={`/explore/themes/${theme.id}`}
              className="w-44 sm:w-48 lg:w-52 flex-shrink-0"
              role="listitem"
            >
              <div
                className={`relative h-28 rounded-2xl overflow-hidden bg-gradient-to-br ${getGradient(theme.name, index)} border border-white/[0.06] transition-all duration-300 hover:border-white/[0.12] hover:shadow-xl hover:shadow-black/30 hover:-translate-y-1 group/card`}
              >
                {/* Subtle texture overlay */}
                <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-white/[0.04] pointer-events-none" />

                {/* Content */}
                <div className="relative flex flex-col justify-end h-full p-4">
                  <h3 className="text-base font-bold text-white leading-tight group-hover/card:text-brand-300 transition-colors">
                    {theme.name}
                  </h3>
                  <p className="text-xs text-white/60 mt-1">
                    {theme.gameCount} {theme.gameCount === 1 ? "game" : "games"}
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
